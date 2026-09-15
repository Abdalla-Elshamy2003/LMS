package com.manarah.calendar;

import com.manarah.calendar.domain.ScheduleSlot;
import com.manarah.calendar.repo.ScheduleSlotRepository;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.*;
import com.manarah.exam.repo.ExamRepository;
import com.manarah.homework.repo.AssignmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.repo.RoomRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ScheduleService {
    private final ScheduleSlotRepository slots;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final StudyGroupRepository groups;
    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository studentGuardians;
    private final UserRepository users;
    private final RoomRepository rooms;
    private final AssignmentRepository assignments;
    private final ExamRepository exams;

    public ScheduleService(ScheduleSlotRepository slots, CourseRepository courses, EnrollmentRepository enrollments,
            StudyGroupRepository groups, StudentRepository students, GuardianRepository guardians,
            StudentGuardianRepository studentGuardians, UserRepository users, RoomRepository rooms,
            AssignmentRepository assignments, ExamRepository exams) {
        this.slots = slots; this.courses = courses; this.enrollments = enrollments; this.groups = groups;
        this.students = students; this.guardians = guardians; this.studentGuardians = studentGuardians;
        this.users = users; this.rooms = rooms; this.assignments = assignments; this.exams = exams;
    }

    public record SlotView(Long id, Long courseId, String courseTitle, String subject, Long groupId, String groupName,
            Long teacherId, String teacherName, Long roomId, String roomName, String title, int dayOfWeek,
            LocalTime startTime, LocalTime endTime, String deliveryMode, String meetingUrl, String color,
            String learnerNames) {}
    public record AgendaItem(Long id, String kind, Long courseId, String courseTitle, String subject, String title,
            Instant at, String status) {}
    public record ScheduleView(List<SlotView> slots, List<AgendaItem> agenda, List<String> subjects) {}
    public record SaveRequest(Long courseId, Long groupId, Long teacherId, Long roomId, String title, Integer dayOfWeek,
            LocalTime startTime, LocalTime endTime, String deliveryMode, String meetingUrl, String color) {}

    public ScheduleView view(UserPrincipal actor) {
        Long tenant = actor.getTenantId();
        List<Course> visibleCourses;
        Map<Long, Set<Long>> visibleGroups = new HashMap<>();
        Map<String, List<String>> learnerNames = new HashMap<>();
        if (actor.getRole() == Role.STUDENT) {
            Long sid = students.findByTenantIdAndUserId(tenant, actor.getId())
                    .orElseThrow(() -> new ForbiddenException("لا يوجد ملف طالب مرتبط بالحساب")).getId();
            var active = enrollments.findByTenantIdAndStudentId(tenant, sid).stream()
                    .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).toList();
            active.forEach(e -> visibleGroups.computeIfAbsent(e.getCourseId(), ignored -> new HashSet<>()).add(e.getGroupId()));
            Set<Long> ids = visibleGroups.keySet();
            visibleCourses = courses.findByTenantId(tenant).stream().filter(c -> ids.contains(c.getId())).toList();
        } else if (actor.getRole() == Role.PARENT) {
            var guardian = guardians.findByTenantIdAndUserId(tenant, actor.getId()).orElse(null);
            if (guardian != null) {
                for (var link : studentGuardians.findByTenantIdAndGuardianId(tenant, guardian.getId())) {
                    var child = students.findByTenantIdAndId(tenant, link.getStudentId()).orElse(null);
                    if (child == null) continue;
                    enrollments.findByTenantIdAndStudentId(tenant, child.getId()).stream()
                            .filter(e -> Set.of("ACTIVE", "COMPLETED").contains(e.getStatus())).forEach(e -> {
                                visibleGroups.computeIfAbsent(e.getCourseId(), ignored -> new HashSet<>()).add(e.getGroupId());
                                learnerNames.computeIfAbsent(slotKey(e.getCourseId(), e.getGroupId()), ignored -> new ArrayList<>()).add(child.getFullName());
                                learnerNames.computeIfAbsent(slotKey(e.getCourseId(), null), ignored -> new ArrayList<>()).add(child.getFullName());
                            });
                }
            }
            Set<Long> ids = visibleGroups.keySet();
            visibleCourses = courses.findByTenantId(tenant).stream().filter(c -> ids.contains(c.getId())).toList();
        } else if (actor.getRole() == Role.TEACHER) {
            visibleCourses = courses.findByTenantIdAndTeacherId(tenant, actor.getId());
        } else if (actor.isAdmin() || actor.getRole() == Role.CONTENT_MANAGER || actor.getRole() == Role.ASSISTANT) {
            visibleCourses = courses.findByTenantId(tenant);
        } else {
            visibleCourses = List.of();
        }
        Map<Long, Course> courseMap = new HashMap<>();
        visibleCourses.forEach(c -> courseMap.put(c.getId(), c));
        List<SlotView> visibleSlots = slots.findByTenantIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(tenant).stream()
                .filter(s -> courseMap.containsKey(s.getCourseId()))
                .filter(s -> !Set.of(Role.STUDENT, Role.PARENT).contains(actor.getRole()) || s.getGroupId() == null
                        || visibleGroups.getOrDefault(s.getCourseId(), Set.of()).contains(s.getGroupId()))
                .map(s -> toView(s, courseMap.get(s.getCourseId()), actor.getRole() == Role.PARENT
                        ? String.join("، ", learnerNames.getOrDefault(slotKey(s.getCourseId(), s.getGroupId()), List.of())) : null)).toList();
        Set<Long> courseIds = courseMap.keySet();
        List<AgendaItem> agenda = new ArrayList<>();
        assignments.findByTenantId(tenant).stream().filter(a -> courseIds.contains(a.getCourseId()) && a.getDeadline() != null)
                .forEach(a -> { Course c = courseMap.get(a.getCourseId()); agenda.add(new AgendaItem(a.getId(), "HOMEWORK", c.getId(), c.getTitle(), c.getSubject(), a.getTitle(), a.getDeadline(), "UPCOMING")); });
        exams.findByTenantId(tenant).stream().filter(e -> courseIds.contains(e.getCourseId()) && e.getStartAt() != null && !"DRAFT".equals(e.getStatus()))
                .forEach(e -> { Course c = courseMap.get(e.getCourseId()); agenda.add(new AgendaItem(e.getId(), "EXAM", c.getId(), c.getTitle(), c.getSubject(), e.getTitle(), e.getStartAt(), e.getStatus())); });
        agenda.sort(Comparator.comparing(AgendaItem::at));
        List<String> subjects = visibleCourses.stream().map(Course::getSubject).filter(Objects::nonNull).distinct().sorted().toList();
        return new ScheduleView(visibleSlots, agenda, subjects);
    }

    @Transactional
    public SlotView create(UserPrincipal actor, SaveRequest req) {
        validate(req);
        Course course = canManage(actor, req.courseId());
        ScheduleSlot s = new ScheduleSlot(); s.setTenantId(actor.getTenantId());
        apply(actor, course, s, req); checkConflict(actor.getTenantId(), s, null);
        return toView(slots.save(s), course, null);
    }

    @Transactional
    public SlotView update(UserPrincipal actor, Long id, SaveRequest req) {
        validate(req);
        ScheduleSlot s = slots.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("الموعد", id));
        Course course = canManage(actor, req.courseId());
        apply(actor, course, s, req); checkConflict(actor.getTenantId(), s, id);
        return toView(slots.save(s), course, null);
    }

    @Transactional
    public void delete(UserPrincipal actor, Long id) {
        ScheduleSlot s = slots.findByTenantIdAndId(actor.getTenantId(), id).orElseThrow(() -> NotFoundException.of("الموعد", id));
        canManage(actor, s.getCourseId()); s.setActive(false); slots.save(s);
    }

    private Course canManage(UserPrincipal actor, Long courseId) {
        Course c = courses.findByTenantIdAndId(actor.getTenantId(), courseId).orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        if (actor.isAdmin() || actor.getRole() == Role.CONTENT_MANAGER || (actor.getRole() == Role.TEACHER && Objects.equals(c.getTeacherId(), actor.getId()))) return c;
        throw new ForbiddenException("يمكن للمدرس تنظيم مواعيد كورساته فقط");
    }
    private void validate(SaveRequest r) {
        if (r.courseId() == null || r.dayOfWeek() == null || r.dayOfWeek() < 0 || r.dayOfWeek() > 6 || r.startTime() == null || r.endTime() == null)
            throw new BadRequestException("اختر الكورس واليوم ووقت البداية والنهاية");
        if (!r.endTime().isAfter(r.startTime())) throw new BadRequestException("وقت النهاية يجب أن يكون بعد البداية");
        if (r.deliveryMode() != null && !Set.of("IN_PERSON", "ONLINE", "HYBRID").contains(r.deliveryMode())) throw new BadRequestException("طريقة الحضور غير مدعومة");
        if (r.meetingUrl() != null && !r.meetingUrl().isBlank() && !r.meetingUrl().matches("(?i)^https?://[^\\s]+$")) throw new BadRequestException("رابط الحصة غير صالح");
    }
    private void apply(UserPrincipal actor, Course c, ScheduleSlot s, SaveRequest r) {
        s.setCourseId(c.getId());
        if (r.groupId() != null) {
            var group = groups.findByTenantIdAndId(actor.getTenantId(), r.groupId()).orElseThrow(() -> NotFoundException.of("المجموعة", r.groupId()));
            if (!Objects.equals(group.getCourseId(), c.getId())) throw new BadRequestException("المجموعة لا تتبع هذا الكورس");
        }
        s.setGroupId(r.groupId());
        Long teacherId = actor.getRole() == Role.TEACHER ? actor.getId() : r.teacherId() != null ? r.teacherId() : c.getTeacherId();
        if (teacherId != null) users.findByTenantIdAndId(actor.getTenantId(), teacherId)
                .filter(u -> u.getRole() == Role.TEACHER).orElseThrow(() -> new BadRequestException("المدرس المحدد غير صالح"));
        if (r.roomId() != null) rooms.findByTenantIdAndId(actor.getTenantId(), r.roomId())
                .orElseThrow(() -> new BadRequestException("القاعة المحددة غير صالحة"));
        s.setTeacherId(teacherId);
        s.setRoomId(r.roomId()); s.setTitle(r.title() == null || r.title().isBlank() ? "حصة " + c.getSubject() : r.title().trim());
        s.setDayOfWeek(r.dayOfWeek()); s.setStartTime(r.startTime()); s.setEndTime(r.endTime());
        s.setDeliveryMode(r.deliveryMode() == null ? "IN_PERSON" : r.deliveryMode()); s.setMeetingUrl(r.meetingUrl());
        s.setColor(r.color() != null && r.color().matches("^#[0-9a-fA-F]{6}$") ? r.color() : "#0f766e"); s.setActive(true);
    }
    private void checkConflict(Long tenant, ScheduleSlot candidate, Long ownId) {
        boolean conflict = slots.findByTenantIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(tenant).stream()
                .filter(s -> !Objects.equals(s.getId(), ownId) && s.getDayOfWeek() == candidate.getDayOfWeek())
                .filter(s -> candidate.getStartTime().isBefore(s.getEndTime()) && candidate.getEndTime().isAfter(s.getStartTime()))
                .anyMatch(s -> (candidate.getTeacherId() != null && Objects.equals(s.getTeacherId(), candidate.getTeacherId())) || (candidate.getRoomId() != null && Objects.equals(s.getRoomId(), candidate.getRoomId())) || (candidate.getGroupId() != null && Objects.equals(s.getGroupId(), candidate.getGroupId())));
        if (conflict) throw new ConflictException("يوجد تعارض مع موعد آخر للمدرس أو المجموعة أو القاعة");
    }
    private SlotView toView(ScheduleSlot s, Course c, String learnerNames) {
        return new SlotView(s.getId(), c.getId(), c.getTitle(), c.getSubject(), s.getGroupId(), s.getGroupId() == null ? null : groups.findByTenantIdAndId(s.getTenantId(), s.getGroupId()).map(g -> g.getName()).orElse(null),
                s.getTeacherId(), s.getTeacherId() == null ? null : users.findByTenantIdAndId(s.getTenantId(), s.getTeacherId()).map(u -> u.getFullName()).orElse(null),
                s.getRoomId(), s.getRoomId() == null ? null : rooms.findByTenantIdAndId(s.getTenantId(), s.getRoomId()).map(r -> r.getName()).orElse(null), s.getTitle(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getDeliveryMode(), s.getMeetingUrl(), s.getColor(), learnerNames);
    }

    private static String slotKey(Long courseId, Long groupId) { return courseId + ":" + (groupId == null ? "all" : groupId); }
}
