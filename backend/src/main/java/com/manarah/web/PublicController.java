package com.manarah.web;

import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.course.repo.CourseRepository;
import com.manarah.course.repo.CourseModuleRepository;
import com.manarah.course.repo.LessonRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.repo.TenantRepository;
import com.manarah.student.repo.StudentRepository;
import com.manarah.web.RegistrationService.RegisterCommand;
import com.manarah.web.RegistrationService.RegistrationResult;
import com.manarah.web.RegistrationService.CheckoutCommand;
import com.manarah.web.RegistrationService.CheckoutResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.manarah.security.FileSessionCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Public, unauthenticated endpoints powering the marketing landing page + trial registration. */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public / Landing")
public class PublicController {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final EnrollmentRepository enrollments;
    private final RegistrationService registrationService;
    private final FileSessionCookie fileSessionCookie;
    private final com.manarah.academy.TeacherAcademyRepository academies;

    public PublicController(TenantRepository tenants, UserRepository users, CourseRepository courses,
                           StudentRepository students, CourseModuleRepository modules, LessonRepository lessons,
                           EnrollmentRepository enrollments, RegistrationService registrationService,
                           FileSessionCookie fileSessionCookie, com.manarah.academy.TeacherAcademyRepository academies) {
        this.academies = academies;
        this.tenants = tenants;
        this.users = users;
        this.courses = courses;
        this.students = students;
        this.modules = modules;
        this.lessons = lessons;
        this.enrollments = enrollments;
        this.registrationService = registrationService;
        this.fileSessionCookie = fileSessionCookie;
    }

    @GetMapping("/teachers/{id}")
    public Map<String, Object> teacher(@PathVariable Long id) {
        var teacher = users.findById(id).filter(u -> u.getRole() == Role.TEACHER)
                .filter(u -> publiclyVisible(u.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("المدرس", id));
        var taught = courses.findByTenantIdAndTeacherId(teacher.getTenantId(), id).stream()
                .filter(c -> "ACTIVE".equals(c.getStatus())).toList();
        List<Map<String, Object>> courseList = taught.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId()); item.put("title", c.getTitle()); item.put("subject", nn(c.getSubject(), ""));
            item.put("gradeLevel", nn(c.getGradeLevel(), "")); item.put("grade", nn(c.getGrade(), ""));
            item.put("schedule", nn(c.getSchedule(), "")); item.put("price", c.getPrice());
            item.put("discountPercent", c.getDiscountPercent()); item.put("finalPrice", c.getFinalPrice());
            item.put("coverUrl", nn(c.getCoverUrl(), ""));
            item.put("students", enrollments.countStudying(teacher.getTenantId(), c.getId()));
            item.put("description", nn(c.getDescription(), ""));
            return item;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", teacher.getId()); result.put("name", teacher.getFullName()); result.put("title", nn(teacher.getTitle(), "مدرس"));
        result.put("subjects", nn(teacher.getSubjects(), "")); result.put("bio", nn(teacher.getBio(), "خبرة واسعة في التدريس وشرح مبسّط."));
        result.put("photoUrl", nn(teacher.getPhotoUrl(), "")); result.put("schedule", nn(teacher.getSchedule(), ""));
        result.put("courseCount", taught.size()); result.put("studentCount", courseList.stream().mapToLong(item -> ((Number)item.get("students")).longValue()).sum());
        result.put("courses", courseList);
        return result;
    }

    @GetMapping("/courses/{id}")
    public Map<String, Object> course(@PathVariable Long id) {
        var c = courses.findById(id).filter(course -> "ACTIVE".equals(course.getStatus()))
                .filter(course -> publiclyVisible(course.getTenantId()))
                .orElseThrow(() -> NotFoundException.of("الكورس", id));
        var teacher = c.getTeacherId() == null ? null : users.findByTenantIdAndId(c.getTenantId(), c.getTeacherId()).orElse(null);
        List<Map<String, Object>> curriculum = modules.findByTenantIdAndCourseIdOrderByPosition(c.getTenantId(), c.getId()).stream().map(module -> {
            var rows = lessons.findByTenantIdAndModuleIdOrderByPosition(c.getTenantId(), module.getId());
            return Map.<String, Object>of("id", module.getId(), "title", module.getTitle(), "lessonCount", rows.size(),
                    "durationMin", rows.stream().mapToInt(l -> l.getDurationMin()).sum(),
                    "lessons", rows.stream().map(l -> Map.of("id", l.getId(), "title", l.getTitle(), "durationMin", l.getDurationMin())).toList());
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", c.getId()); result.put("title", c.getTitle()); result.put("subject", nn(c.getSubject(), ""));
        result.put("description", nn(c.getDescription(), "")); result.put("gradeLevel", nn(c.getGradeLevel(), "")); result.put("grade", nn(c.getGrade(), ""));
        result.put("price", c.getPrice()); result.put("discountPercent", c.getDiscountPercent()); result.put("finalPrice", c.getFinalPrice());
        result.put("coverUrl", nn(c.getCoverUrl(), "")); result.put("schedule", nn(c.getSchedule(), ""));
        result.put("studentCount", enrollments.countStudying(c.getTenantId(), c.getId())); result.put("curriculum", curriculum);
        result.put("teacher", teacher == null ? null : Map.of("id", teacher.getId(), "name", teacher.getFullName(), "title", nn(teacher.getTitle(), "مدرس"), "photoUrl", nn(teacher.getPhotoUrl(), ""), "subjects", nn(teacher.getSubjects(), "")));
        // Checkout needs to know which tenant the course lives in, since a teacher page is its own tenant.
        result.put("tenantSlug", tenants.findById(c.getTenantId()).map(t -> t.getSlug()).orElse(null));
        var academy = academies.findByTenantId(c.getTenantId());
        result.put("academySlug", academy.map(a -> a.getSlug()).orElse(null));
        result.put("payment", academy.map(a -> Map.of(
                "instapayNumber", nn(a.getInstapayNumber(), ""),
                "vodafoneCashNumber", nn(a.getVodafoneCashNumber(), ""),
                "note", nn(a.getPaymentNote(), "")))
                .orElse(Map.of("instapayNumber", "", "vodafoneCashNumber", "", "note", "")));
        return result;
    }

    /** An academy tenant is public only while its page is published; the main tenant always is. */
    private boolean publiclyVisible(Long tenantId) {
        return academies.findByTenantId(tenantId).map(a -> a.isPublished()).orElse(true);
    }

    @GetMapping("/landing")
    public Map<String, Object> landing(@RequestParam(required = false) String tenant) {
        var t = (tenant != null ? tenants.findBySlug(tenant) : tenants.findAll().stream().findFirst())
                .orElseThrow(() -> new NotFoundException("لا توجد مؤسسة"));
        Long id = t.getId();

        List<Map<String, Object>> teacherList = users.findByTenantIdAndRole(id, Role.TEACHER).stream().map(u ->
                Map.<String, Object>of(
                        "id", u.getId(),
                        "name", u.getFullName(),
                        "title", nn(u.getTitle(), "مدرس"),
                        "subjects", nn(u.getSubjects(), ""),
                        "bio", nn(u.getBio(), "خبرة واسعة في التدريس وشرح مبسّط يوصل المعلومة."),
                        "photoUrl", nn(u.getPhotoUrl(), ""),
                        "courses", courses.findByTenantIdAndTeacherId(id, u.getId()).size())).toList();

        List<Map<String, Object>> courseList = courses.findByTenantId(id).stream().map(c -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", c.getId()); item.put("title", c.getTitle()); item.put("subject", nn(c.getSubject(), ""));
                    item.put("gradeLevel", nn(c.getGradeLevel(), "")); item.put("price", c.getPrice());
                    item.put("discountPercent", c.getDiscountPercent()); item.put("finalPrice", c.getFinalPrice());
                    item.put("teacherName", c.getTeacherId() == null ? "" : users.findById(c.getTeacherId()).map(u -> u.getFullName()).orElse(""));
                    item.put("coverUrl", nn(c.getCoverUrl(), ""));
                    return item;
                }).toList();

        return Map.of(
                "tenant", Map.of("name", t.getName(), "slug", t.getSlug(), "color", nn(t.getPrimaryColor(), "#0284c7")),
                "stats", Map.of(
                        "students", students.countByTenantId(id),
                        "teachers", users.countByTenantIdAndRole(id, Role.TEACHER),
                        "courses", courses.countByTenantId(id)),
                "teachers", teacherList,
                "courses", courseList);
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResult> register(@RequestBody RegisterCommand cmd) {
        var result = registrationService.register(cmd);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, fileSessionCookie.issue(result.accessToken())).body(result);
    }

    /** Buying a specific course from a teacher's public profile — creates the account, a real
     *  (non-trial) enrollment, and an invoice for the course price. */
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResult> checkout(@RequestBody CheckoutCommand cmd) {
        var result = registrationService.checkout(cmd);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, fileSessionCookie.issue(result.accessToken())).body(result);
    }

    /** Redeems a manual-payment code (InstaPay / Vodafone Cash confirmed by the teacher) and opens
     *  the course it belongs to immediately — no payment gateway involved. */
    @PostMapping("/redeem-code")
    public ResponseEntity<RegistrationResult> redeemCode(@RequestBody RegistrationService.RedeemCommand cmd) {
        var result = registrationService.redeem(cmd);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, fileSessionCookie.issue(result.accessToken())).body(result);
    }

    private static String nn(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
