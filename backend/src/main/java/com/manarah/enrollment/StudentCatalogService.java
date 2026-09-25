package com.manarah.enrollment;

import com.manarah.academy.LinkedStudentAccounts;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.common.SchoolYears;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.course.domain.Course;
import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The student's courses with their current teacher: what they study, what waits for payment, and everything the
 * teacher offers for their school year. Worked out on every read, so a course the teacher publishes for "تانية ثانوي"
 * shows up for every تانية ثانوي student at once, without creating anything for them — they still subscribe (and pay)
 * per course. A course with no year set is for every year.
 */
@Service
public class StudentCatalogService {
    /** How long a course the student hasn't taken counts as "جديد". */
    private static final Duration NEW_FOR = Duration.ofDays(21);
    private static final List<String> ORDER = List.of("ACTIVE", "PENDING", "COMPLETED", "NONE", "CLOSED");

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final TeacherAcademyRepository academies;
    private final LinkedStudentAccounts linked;
    private final CourseRequests requests;

    public StudentCatalogService(CourseRepository courses, EnrollmentRepository enrollments, StudentRepository students,
                                 TeacherAcademyRepository academies, LinkedStudentAccounts linked, CourseRequests requests) {
        this.courses = courses; this.enrollments = enrollments; this.students = students;
        this.academies = academies; this.linked = linked; this.requests = requests;
    }

    public record Teacher(String name, String subject, String slug, String photoUrl) {}
    public record Payment(String instapayNumber, String vodafoneCashNumber, String note) {}
    public record CatalogCourse(Long id, String title, String subject, String year, String description, String coverUrl,
                                BigDecimal price, BigDecimal finalPrice, int discountPercent, boolean free,
                                String state, boolean isNew, boolean forYear) {}
    public record Catalog(String grade, List<String> years, Teacher teacher, Payment payment, List<CatalogCourse> courses) {}

    public Catalog forStudent(UserPrincipal actor) {
        Student me = student(actor);
        Long tenantId = actor.getTenantId();
        String yearKey = SchoolYears.key(me.getGrade());
        Map<Long, Enrollment> mine = enrollments.findByTenantIdAndStudentId(tenantId, me.getId()).stream()
                .collect(Collectors.toMap(Enrollment::getCourseId, Function.identity(), (a, b) -> a));
        List<Course> all = courses.findByTenantId(tenantId);
        Instant fresh = Instant.now().minus(NEW_FOR);

        List<CatalogCourse> list = all.stream().map(c -> {
            String state = CourseRequests.stateOf(mine.get(c.getId()));
            boolean has = !"NONE".equals(state) && !"CLOSED".equals(state);
            boolean yearMatch = SchoolYears.same(me.getGrade(), c.getGrade());
            boolean offered = "ACTIVE".equals(c.getStatus()) && (yearKey.isEmpty() || blank(c.getGrade()) || yearMatch);
            if (!has && !offered) return null;
            boolean isNew = "NONE".equals(state) && c.getCreatedAt() != null && c.getCreatedAt().isAfter(fresh);
            return new CatalogCourse(c.getId(), c.getTitle(), Objects.toString(c.getSubject(), ""), Objects.toString(c.getGrade(), ""),
                    Objects.toString(c.getDescription(), ""), Objects.toString(c.getCoverUrl(), ""), c.getPrice(), c.getFinalPrice(),
                    c.getDiscountPercent() == null ? 0 : c.getDiscountPercent(), CourseRequests.isFree(c), state, isNew, yearMatch);
        }).filter(Objects::nonNull).sorted(Comparator.comparingInt((CatalogCourse c) -> ORDER.indexOf(c.state()))
                .thenComparing(CatalogCourse::isNew, Comparator.reverseOrder())
                .thenComparing(CatalogCourse::id, Comparator.reverseOrder())).toList();

        // The years this teacher teaches, one spelling each, for the "سنتك الدراسية" picker.
        Map<String, String> years = new TreeMap<>();
        all.stream().filter(c -> "ACTIVE".equals(c.getStatus()) && !blank(c.getGrade()))
                .forEach(c -> years.putIfAbsent(SchoolYears.key(c.getGrade()), c.getGrade().trim()));

        var academy = academies.findByTenantId(tenantId);
        Teacher teacher = academy.map(a -> new Teacher(a.getName(), Objects.toString(a.getSubject(), ""), a.getSlug(),
                Objects.toString(a.getPhotoUrl(), ""))).orElse(null);
        Payment payment = academy.filter(a -> !blank(a.getInstapayNumber()) || !blank(a.getVodafoneCashNumber()))
                .map(a -> new Payment(Objects.toString(a.getInstapayNumber(), ""), Objects.toString(a.getVodafoneCashNumber(), ""),
                        Objects.toString(a.getPaymentNote(), ""))).orElse(null);
        return new Catalog(Objects.toString(me.getGrade(), ""), new ArrayList<>(years.values()), teacher, payment, list);
    }

    /** Ask for one of the current teacher's courses. Returns the new state (ACTIVE for a free course, else PENDING). */
    @Transactional
    public String request(UserPrincipal actor, Long courseId) {
        Student me = student(actor);
        Course course = courses.findByTenantIdAndId(actor.getTenantId(), courseId)
                .orElseThrow(() -> NotFoundException.of("الكورس", courseId));
        return requests.request(actor.getTenantId(), me.getId(), course);
    }

    /** The student's school year, on every teacher they have: it's the same year wherever they study. */
    @Transactional
    public void setGrade(UserPrincipal actor, String grade) {
        student(actor);
        String g = grade == null ? "" : grade.trim();
        if (g.isEmpty() || g.length() > 60) throw new BadRequestException("اختار سنتك الدراسية");
        for (Student seat : linked.seatsOf(actor)) { seat.setGrade(g); students.save(seat); }
    }

    private Student student(UserPrincipal actor) {
        if (actor.getRole() != Role.STUDENT) throw new ForbiddenException("الصفحة دي للطلاب");
        return students.findByTenantIdAndUserId(actor.getTenantId(), actor.getId())
                .orElseThrow(() -> new ForbiddenException("لا يوجد ملف طالب مرتبط بالحساب"));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
