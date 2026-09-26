package com.manarah.center;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a center runs day to day: its settings, its teachers, each teacher's groups (days, hour, fee per session) and
 * the students of every group, each with the card their QR is printed on.
 */
@Service
public class CenterService {
    private static final int MAX_BULK = 200;

    private final CenterScope scope;
    private final CenterRepository centers;
    private final CenterTeacherRepository teachers;
    private final CenterGroupRepository groups;
    private final CenterStudentRepository students;
    private final CenterSessionRepository sessions;
    private final CenterAttendanceRepository attendance;
    private final CenterBookRepository books;
    private final CenterBookReservationRepository reservations;
    private final CenterCodes codes;
    private final UserRepository users;
    private final com.manarah.audit.AuditService audit;

    public CenterService(CenterScope scope, CenterRepository centers, CenterTeacherRepository teachers, CenterGroupRepository groups,
                         CenterStudentRepository students, CenterSessionRepository sessions, CenterAttendanceRepository attendance,
                         CenterBookRepository books, CenterBookReservationRepository reservations, CenterCodes codes,
                         UserRepository users, com.manarah.audit.AuditService audit) {
        this.scope = scope; this.centers = centers; this.teachers = teachers; this.groups = groups; this.students = students;
        this.sessions = sessions; this.attendance = attendance; this.books = books; this.reservations = reservations;
        this.codes = codes; this.users = users; this.audit = audit;
    }

    // ---- The center itself ------------------------------------------------------------------------------------------

    public record CenterView(Long id, String name, String phone, String address, boolean autoPay, String username) {}
    public record CenterInput(String name, String phone, String address, Boolean autoPay) {}

    public CenterView me(UserPrincipal actor) { return view(scope.require(actor)); }

    @Transactional
    public CenterView saveMe(UserPrincipal actor, CenterInput in) {
        Center c = scope.require(actor);
        c.setName(required(in.name(), 120, "اسم السنتر"));
        c.setPhone(optional(in.phone(), 40));
        c.setAddress(optional(in.address(), 250));
        if (in.autoPay() != null) c.setAutoPay(in.autoPay());
        c.setUpdatedAt(Instant.now());
        centers.save(c);
        audit.record(actor, "CENTER_SETTINGS_SAVED", "Center", c.getId(), null, c.getName() + "/autoPay=" + c.isAutoPay());
        return view(c);
    }

    private CenterView view(Center c) {
        String username = users.findById(c.getOwnerUserId()).map(u -> u.getUsername()).orElse(null);
        return new CenterView(c.getId(), c.getName(), c.getPhone(), c.getAddress(), c.isAutoPay(), username);
    }

    // ---- Teachers ---------------------------------------------------------------------------------------------------

    public record TeacherView(Long id, String name, String subject, String phone, int centerPercent, boolean active,
                              long groups, long students) {}
    public record TeacherInput(String name, String subject, String phone, Integer centerPercent, Boolean active) {}

    public List<TeacherView> teachers(UserPrincipal actor) {
        Long tenantId = scope.require(actor).getTenantId();
        List<CenterGroup> all = groups.findByTenantId(tenantId);
        Map<Long, Long> perGroup = activeStudentsPerGroup(tenantId);
        return teachers.findByTenantIdOrderByName(tenantId).stream().map(t -> {
            List<CenterGroup> own = all.stream().filter(g -> g.getTeacherId().equals(t.getId())).toList();
            long count = own.stream().mapToLong(g -> perGroup.getOrDefault(g.getId(), 0L)).sum();
            return new TeacherView(t.getId(), t.getName(), t.getSubject(), t.getPhone(), t.getCenterPercent(), t.isActive(), own.size(), count);
        }).sorted(Comparator.comparing((TeacherView v) -> !v.active()).thenComparing(TeacherView::name)).toList();
    }

    @Transactional
    public TeacherView saveTeacher(UserPrincipal actor, Long id, TeacherInput in) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterTeacher t = id == null ? new CenterTeacher() : teacher(tenantId, id);
        if (id == null) t.setTenantId(tenantId);
        t.setName(required(in.name(), 120, "اسم المدرس"));
        t.setSubject(required(in.subject(), 80, "المادة"));
        t.setPhone(optional(in.phone(), 40));
        int percent = in.centerPercent() == null ? t.getCenterPercent() : in.centerPercent();
        if (percent < 0 || percent > 100) throw new BadRequestException("نسبة السنتر من ٠ لحد ١٠٠٪");
        t.setCenterPercent(percent);
        if (in.active() != null) t.setActive(in.active());
        teachers.save(t);
        audit.record(actor, id == null ? "CENTER_TEACHER_CREATED" : "CENTER_TEACHER_UPDATED", "CenterTeacher", t.getId(), null,
                t.getName() + "/" + t.getSubject() + "/" + t.getCenterPercent() + "%");
        return teachers(actor).stream().filter(v -> v.id().equals(t.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteTeacher(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterTeacher t = teacher(tenantId, id);
        if (groups.existsByTenantIdAndTeacherId(tenantId, id) || books.existsByTenantIdAndTeacherId(tenantId, id))
            throw new ConflictException("المدرس ده ليه مجموعات أو كتب — وقّفه بدل ما تمسحه عشان حساباته تفضل موجودة");
        teachers.delete(t);
        audit.record(actor, "CENTER_TEACHER_DELETED", "CenterTeacher", id, t.getName(), null);
    }

    CenterTeacher teacher(Long tenantId, Long id) {
        if (id == null) throw new BadRequestException("اختار المدرس");
        return teachers.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("المدرس", id));
    }

    // ---- Groups -----------------------------------------------------------------------------------------------------

    public record GroupView(Long id, Long teacherId, String teacherName, String name, String subject, String grade,
                            List<Integer> days, String daysLabel, String startTime, String endTime, String timeLabel,
                            String room, BigDecimal sessionPrice, boolean active, long students) {}
    public record GroupInput(Long teacherId, String name, String subject, String grade, List<Integer> days, String startTime,
                             String endTime, String room, BigDecimal sessionPrice, Boolean active) {}

    public List<GroupView> groups(UserPrincipal actor, Long teacherId) {
        Long tenantId = scope.require(actor).getTenantId();
        Map<Long, CenterTeacher> byId = teacherMap(tenantId);
        Map<Long, Long> perGroup = activeStudentsPerGroup(tenantId);
        List<CenterGroup> list = teacherId == null ? groups.findByTenantId(tenantId) : groups.findByTenantIdAndTeacherId(tenantId, teacherId);
        return list.stream().map(g -> groupView(g, byId.get(g.getTeacherId()), perGroup.getOrDefault(g.getId(), 0L)))
                .sorted(Comparator.comparing((GroupView v) -> !v.active()).thenComparing(GroupView::teacherName)
                        .thenComparing(v -> v.days().isEmpty() ? 9 : weekRank(v.days().get(0))).thenComparing(GroupView::startTime))
                .toList();
    }

    @Transactional
    public GroupView saveGroup(UserPrincipal actor, Long id, GroupInput in) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterGroup g = id == null ? new CenterGroup() : group(tenantId, id);
        if (id == null) g.setTenantId(tenantId);
        CenterTeacher t = teacher(tenantId, in.teacherId() != null ? in.teacherId() : g.getTeacherId());
        g.setTeacherId(t.getId());
        g.setSubject(in.subject() == null || in.subject().isBlank() ? t.getSubject() : required(in.subject(), 80, "المادة"));
        g.setGrade(optional(in.grade(), 80));
        g.setDays(CenterDays.format(in.days()));
        g.setStartTime(CenterDays.time(in.startTime(), true));
        g.setEndTime(CenterDays.time(in.endTime(), false));
        if (g.getEndTime() != null && g.getEndTime().compareTo(g.getStartTime()) <= 0)
            throw new BadRequestException("ميعاد النهاية لازم يبقى بعد ميعاد البداية");
        g.setRoom(optional(in.room(), 60));
        BigDecimal price = in.sessionPrice() == null ? BigDecimal.ZERO : in.sessionPrice();
        if (price.signum() < 0 || price.compareTo(BigDecimal.valueOf(100000)) > 0) throw new BadRequestException("سعر الحصة غير صحيح");
        g.setSessionPrice(price);
        String name = in.name() == null || in.name().isBlank()
                ? CenterDays.label(g.getDays()) + " " + CenterDays.timeLabel(g.getStartTime(), null) : in.name();
        g.setName(required(name, 120, "اسم المجموعة"));
        if (in.active() != null) g.setActive(in.active());
        groups.save(g);
        audit.record(actor, id == null ? "CENTER_GROUP_CREATED" : "CENTER_GROUP_UPDATED", "CenterGroup", g.getId(), null,
                g.getName() + "/" + g.getDays() + "@" + g.getStartTime() + "/" + g.getSessionPrice());
        return groupView(g, t, activeStudentsPerGroup(tenantId).getOrDefault(g.getId(), 0L));
    }

    @Transactional
    public void deleteGroup(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterGroup g = group(tenantId, id);
        if (students.existsByTenantIdAndGroupId(tenantId, id))
            throw new ConflictException("المجموعة فيها طلاب — انقلهم أو وقّف المجموعة بدل ما تمسحها");
        sessions.findByTenantIdAndGroupIdOrderBySessionDateDesc(tenantId, id).forEach(sessions::delete);
        groups.delete(g);
        audit.record(actor, "CENTER_GROUP_DELETED", "CenterGroup", id, g.getName(), null);
    }

    CenterGroup group(Long tenantId, Long id) {
        if (id == null) throw new BadRequestException("اختار المجموعة");
        return groups.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("المجموعة", id));
    }

    GroupView groupView(CenterGroup g, CenterTeacher t, long count) {
        return new GroupView(g.getId(), g.getTeacherId(), t == null ? "" : t.getName(), g.getName(), g.getSubject(), g.getGrade(),
                CenterDays.parse(g.getDays()), CenterDays.label(g.getDays()), g.getStartTime(), g.getEndTime(),
                CenterDays.timeLabel(g.getStartTime(), g.getEndTime()), g.getRoom(), g.getSessionPrice(), g.isActive(), count);
    }

    // ---- Students ---------------------------------------------------------------------------------------------------

    public record StudentView(Long id, String code, String token, String name, String phone, String parentPhone, boolean active,
                              LocalDate joinedOn, Long groupId, String groupName, Long teacherId, String teacherName,
                              String subject, String grade, String daysLabel, String timeLabel, String room, BigDecimal sessionPrice) {}
    public record StudentInput(String name, String phone, String parentPhone) {}
    public record BulkInput(Long groupId, List<StudentInput> students) {}
    public record StudentUpdate(String name, String phone, String parentPhone, Long groupId, Boolean active) {}

    public List<StudentView> students(UserPrincipal actor, Long groupId, Long teacherId, String q) {
        Long tenantId = scope.require(actor).getTenantId();
        Cards cards = cards(tenantId);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return students.findByTenantIdOrderByIdDesc(tenantId).stream()
                .filter(s -> groupId == null || s.getGroupId().equals(groupId))
                .filter(s -> teacherId == null || cards.teacherOf(s).map(t -> t.getId().equals(teacherId)).orElse(false))
                .filter(s -> needle.isEmpty() || s.getName().toLowerCase(Locale.ROOT).contains(needle) || s.getCode().equals(needle)
                        || (s.getPhone() != null && s.getPhone().contains(needle)) || (s.getParentPhone() != null && s.getParentPhone().contains(needle)))
                .map(cards::view).toList();
    }

    public StudentView student(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        return cards(tenantId).view(student(tenantId, id));
    }

    /** Adds students to a group — one name per row — each with their own code and QR. */
    @Transactional
    public List<StudentView> addStudents(UserPrincipal actor, BulkInput in) {
        // The center row is locked before anything else reads it, so two desks adding students never draw the same code.
        Center locked = scope.lock(actor);
        Long tenantId = locked.getTenantId();
        CenterGroup g = group(tenantId, in.groupId());
        if (!g.isActive()) throw new BadRequestException("المجموعة دي موقوفة");
        List<StudentInput> rows = in.students() == null ? List.of() : in.students().stream()
                .filter(r -> r != null && r.name() != null && !r.name().isBlank()).toList();
        if (rows.isEmpty()) throw new BadRequestException("اكتب اسم طالب واحد على الأقل");
        if (rows.size() > MAX_BULK) throw new BadRequestException("أقصى عدد في المرة الواحدة " + MAX_BULK + " طالب");
        LocalDate today = CenterDays.today();
        List<CenterStudent> created = new ArrayList<>();
        for (StudentInput r : rows) {
            CenterStudent s = new CenterStudent();
            s.setTenantId(tenantId);
            s.setGroupId(g.getId());
            s.setName(required(r.name(), 120, "اسم الطالب"));
            s.setPhone(optional(r.phone(), 40));
            s.setParentPhone(optional(r.parentPhone(), 40));
            s.setCode(String.valueOf(locked.getNextStudentCode()));
            locked.setNextStudentCode(locked.getNextStudentCode() + 1);
            s.setToken(codes.cardToken());
            s.setJoinedOn(today);
            created.add(students.save(s));
        }
        centers.save(locked);
        audit.record(actor, "CENTER_STUDENTS_ADDED", "CenterGroup", g.getId(), null, created.size() + " students");
        Cards cards = cards(tenantId);
        return created.stream().map(cards::view).toList();
    }

    @Transactional
    public StudentView updateStudent(UserPrincipal actor, Long id, StudentUpdate in) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterStudent s = student(tenantId, id);
        if (in.name() != null) s.setName(required(in.name(), 120, "اسم الطالب"));
        if (in.phone() != null) s.setPhone(optional(in.phone(), 40));
        if (in.parentPhone() != null) s.setParentPhone(optional(in.parentPhone(), 40));
        String before = "group=" + s.getGroupId() + "/active=" + s.isActive();
        if (in.groupId() != null && !in.groupId().equals(s.getGroupId())) {
            CenterGroup g = group(tenantId, in.groupId());
            if (!g.isActive()) throw new BadRequestException("المجموعة دي موقوفة");
            s.setGroupId(g.getId());
            s.setJoinedOn(CenterDays.today());
        }
        if (in.active() != null) s.setActive(in.active());
        students.save(s);
        audit.record(actor, "CENTER_STUDENT_UPDATED", "CenterStudent", s.getId(), before, "group=" + s.getGroupId() + "/active=" + s.isActive());
        return cards(tenantId).view(s);
    }

    /** A lost card: the old QR stops working the moment the new one is issued. */
    @Transactional
    public StudentView newCard(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterStudent s = student(tenantId, id);
        s.setToken(codes.cardToken());
        students.save(s);
        audit.record(actor, "CENTER_CARD_REISSUED", "CenterStudent", s.getId(), null, null);
        return cards(tenantId).view(s);
    }

    @Transactional
    public void deleteStudent(UserPrincipal actor, Long id) {
        Long tenantId = scope.require(actor).getTenantId();
        CenterStudent s = student(tenantId, id);
        if (attendance.existsByStudentId(id) || reservations.existsByStudentId(id))
            throw new ConflictException("الطالب ده ليه حضور أو حجوزات — وقّفه بدل ما تمسحه عشان الحسابات تفضل مظبوطة");
        students.delete(s);
        audit.record(actor, "CENTER_STUDENT_DELETED", "CenterStudent", id, s.getName(), null);
    }

    CenterStudent student(Long tenantId, Long id) {
        if (id == null) throw new BadRequestException("اختار الطالب");
        return students.findByTenantIdAndId(tenantId, id).orElseThrow(() -> NotFoundException.of("الطالب", id));
    }

    // ---- Overview ---------------------------------------------------------------------------------------------------

    public record TodayGroup(Long groupId, String groupName, String teacherName, String subject, String startTime, String timeLabel,
                             String room, long students, long present, BigDecimal collected) {}
    public record Overview(String centerName, LocalDate date, String dayName, long teachers, long groups, long students,
                           long presentToday, BigDecimal collectedToday, BigDecimal outstandingToday, long openReservations,
                           List<TodayGroup> today, List<UpcomingBook> upcomingBooks) {}
    public record UpcomingBook(Long id, String title, String teacherName, LocalDate releaseDate, long reserved) {}

    public Overview overview(UserPrincipal actor) {
        Center c = scope.require(actor);
        Long tenantId = c.getTenantId();
        LocalDate today = CenterDays.today();
        String todayKey = today.toString();
        Map<Long, CenterTeacher> byTeacher = teacherMap(tenantId);
        Map<Long, Long> perGroup = activeStudentsPerGroup(tenantId);
        List<CenterGroup> all = groups.findByTenantId(tenantId);
        Map<Long, CenterSession> todaySessions = sessions.findByTenantIdAndSessionDateBetween(tenantId, todayKey, todayKey).stream()
                .collect(Collectors.toMap(CenterSession::getGroupId, Function.identity()));
        Map<Long, List<CenterAttendance>> presentBySession = todaySessions.isEmpty() ? Map.of()
                : attendance.findByTenantIdAndSessionIdIn(tenantId, todaySessions.values().stream().map(CenterSession::getId).toList())
                .stream().collect(Collectors.groupingBy(CenterAttendance::getSessionId));

        List<TodayGroup> meeting = new ArrayList<>();
        long present = 0;
        BigDecimal collected = BigDecimal.ZERO, outstanding = BigDecimal.ZERO;
        for (CenterGroup g : all) {
            CenterSession s = todaySessions.get(g.getId());
            List<CenterAttendance> rows = s == null ? List.of() : presentBySession.getOrDefault(s.getId(), List.of());
            BigDecimal got = rows.stream().filter(CenterAttendance::isPaid).map(CenterAttendance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal owed = rows.stream().filter(a -> !a.isPaid()).map(CenterAttendance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            present += rows.size(); collected = collected.add(got); outstanding = outstanding.add(owed);
            if ((g.isActive() && CenterDays.meets(g.getDays(), today)) || s != null) {
                CenterTeacher t = byTeacher.get(g.getTeacherId());
                meeting.add(new TodayGroup(g.getId(), g.getName(), t == null ? "" : t.getName(), g.getSubject(), g.getStartTime(),
                        CenterDays.timeLabel(g.getStartTime(), g.getEndTime()), g.getRoom(),
                        perGroup.getOrDefault(g.getId(), 0L), rows.size(), got));
            }
        }
        meeting.sort(Comparator.comparing(TodayGroup::startTime));

        List<CenterBookReservation> allReservations = reservations.findByTenantIdOrderByIdDesc(tenantId);
        long open = allReservations.stream().filter(r -> CenterBookReservation.RESERVED.equals(r.getStatus())).count();
        List<UpcomingBook> upcoming = books.findByTenantId(tenantId).stream()
                .filter(b -> b.isActive() && b.getReleaseDate() != null && !b.getReleaseDate().isBefore(today.minusDays(7)))
                .sorted(Comparator.comparing(CenterBook::getReleaseDate)).limit(6)
                .map(b -> new UpcomingBook(b.getId(), b.getTitle(),
                        b.getTeacherId() == null ? "" : Optional.ofNullable(byTeacher.get(b.getTeacherId())).map(CenterTeacher::getName).orElse(""),
                        b.getReleaseDate(), allReservations.stream().filter(r -> r.getBookId().equals(b.getId())
                                && !CenterBookReservation.CANCELLED.equals(r.getStatus())).count()))
                .toList();

        return new Overview(c.getName(), today, CenterDays.name(CenterDays.dayOf(today)),
                byTeacher.values().stream().filter(CenterTeacher::isActive).count(),
                all.stream().filter(CenterGroup::isActive).count(),
                students.countByTenantIdAndActiveTrue(tenantId), present, collected, outstanding, open, meeting, upcoming);
    }

    // ---- Shared -----------------------------------------------------------------------------------------------------

    /** The groups and teachers of a tenant, to turn student rows into card views without a query per row. */
    Cards cards(Long tenantId) {
        return new Cards(groups.findByTenantId(tenantId).stream().collect(Collectors.toMap(CenterGroup::getId, Function.identity())),
                teacherMap(tenantId));
    }

    record Cards(Map<Long, CenterGroup> groups, Map<Long, CenterTeacher> teachers) {
        Optional<CenterTeacher> teacherOf(CenterStudent s) {
            return Optional.ofNullable(groups.get(s.getGroupId())).map(g -> teachers.get(g.getTeacherId()));
        }
        StudentView view(CenterStudent s) {
            CenterGroup g = groups.get(s.getGroupId());
            CenterTeacher t = g == null ? null : teachers.get(g.getTeacherId());
            return new StudentView(s.getId(), s.getCode(), s.getToken(), s.getName(), s.getPhone(), s.getParentPhone(), s.isActive(),
                    s.getJoinedOn(), s.getGroupId(), g == null ? "" : g.getName(), t == null ? null : t.getId(), t == null ? "" : t.getName(),
                    g == null ? "" : g.getSubject(), g == null ? null : g.getGrade(), g == null ? "" : CenterDays.label(g.getDays()),
                    g == null ? "" : CenterDays.timeLabel(g.getStartTime(), g.getEndTime()), g == null ? null : g.getRoom(),
                    g == null ? BigDecimal.ZERO : g.getSessionPrice());
        }
    }

    Map<Long, CenterTeacher> teacherMap(Long tenantId) {
        return teachers.findByTenantIdOrderByName(tenantId).stream().collect(Collectors.toMap(CenterTeacher::getId, Function.identity()));
    }

    private Map<Long, Long> activeStudentsPerGroup(Long tenantId) {
        return students.findByTenantIdOrderByIdDesc(tenantId).stream().filter(CenterStudent::isActive)
                .collect(Collectors.groupingBy(CenterStudent::getGroupId, Collectors.counting()));
    }

    /** Saturday first, as the week runs in Egypt. */
    private static int weekRank(int day) { return (day + 1) % 7; }

    static String required(String s, int max, String what) {
        if (s == null || s.isBlank()) throw new BadRequestException("اكتب " + what);
        if (s.trim().length() > max) throw new BadRequestException(what + " أطول من اللازم");
        return s.trim();
    }

    static String optional(String s, int max) {
        if (s == null || s.isBlank()) return null;
        if (s.trim().length() > max) throw new BadRequestException("في خانة أطول من اللازم");
        return s.trim();
    }
}
