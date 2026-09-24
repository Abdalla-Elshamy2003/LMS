package com.manarah.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.academy.*;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.domain.Course;
import com.manarah.course.domain.CourseModule;
import com.manarah.course.domain.Lesson;
import com.manarah.course.domain.LessonMaterial;
import com.manarah.course.repo.CourseModuleRepository;
import com.manarah.course.repo.CourseRepository;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.course.repo.LessonRepository;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Fills the first teacher package with demo content on request from head office: a teacher space for each of its
 * five teachers (with the owner's portraits), six demo video courses each (year, description, price, cover, a short
 * demo lesson video), and links them into the package. The content lives in {@code demo/package-content.json}; the
 * videos and covers ship with the frontend under {@code /videos}.
 *
 * <p>Only ever adds what is missing: a teacher space that already exists (by its link) is left exactly as it is,
 * members already linked stay linked, and no payment numbers are set anywhere — head office sets those itself, so
 * nobody can pay real money for demo content.
 */
@Service
public class DemoPackageSeeder {
    private final TeacherBundleRepository bundles;
    private final TeacherBundleMemberRepository members;
    private final TeacherAcademyRepository academies;
    private final AcademyService academyService;
    private final BundleService bundleService;
    private final UserRepository users;
    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonMaterialRepository materials;
    private final com.manarah.audit.AuditService audit;

    public DemoPackageSeeder(TeacherBundleRepository bundles, TeacherBundleMemberRepository members, TeacherAcademyRepository academies,
                             AcademyService academyService, BundleService bundleService, UserRepository users, CourseRepository courses,
                             CourseModuleRepository modules, LessonRepository lessons, LessonMaterialRepository materials,
                             com.manarah.audit.AuditService audit) {
        this.bundles = bundles; this.members = members; this.academies = academies; this.academyService = academyService;
        this.bundleService = bundleService; this.users = users; this.courses = courses; this.modules = modules;
        this.lessons = lessons; this.materials = materials; this.audit = audit;
    }

    public record Result(int teachersCreated, int coursesCreated, int membersLinked) {}

    @Transactional
    public Result seed(UserPrincipal actor) {
        bundleService.requireHeadOffice(actor);
        JsonNode content = load();
        var bundle = bundles.findBySlug(content.path("bundleSlug").asText())
                .orElseThrow(() -> new NotFoundException("الباقة الأساسية غير موجودة"));
        bundleService.manage(actor, bundle.getId());
        List<TeacherBundleMember> slots = members.findByBundleIdOrderByPositionAsc(bundle.getId());
        int teachersCreated = 0, coursesCreated = 0, linked = 0, i = 0;
        for (JsonNode t : content.path("teachers")) {
            String slug = t.path("slug").asText();
            TeacherAcademy a = academies.findBySlug(slug).orElse(null);
            if (a == null) {
                a = createTeacher(actor, t);
                coursesCreated += createCourses(a, t);
                teachersCreated++;
            }
            if (i < slots.size() && slots.get(i).getAcademyId() == null) {
                TeacherBundleMember m = slots.get(i);
                m.setAcademyId(a.getId());
                members.save(m);
                linked++;
            }
            i++;
        }
        if (bundle.getPrice() == null && content.hasNonNull("price")) {
            bundle.setPrice(new BigDecimal(content.path("price").asText()));
            bundles.save(bundle);
        }
        audit.record(actor, "DEMO_PACKAGE_CONTENT", "TeacherBundle", bundle.getId(), null,
                "teachers=" + teachersCreated + " courses=" + coursesCreated + " linked=" + linked);
        return new Result(teachersCreated, coursesCreated, linked);
    }

    private TeacherAcademy createTeacher(UserPrincipal actor, JsonNode t) {
        // Unusable random password: head office sets the teacher's real credentials from the admin screen.
        String password = "Demo-" + UUID.randomUUID() + "-9a";
        var a = academyService.create(actor, new AcademyService.CreateAcademy(t.path("name").asText(), t.path("slug").asText(),
                t.path("username").asText(), password));
        a.setPhotoUrl(t.path("photo").asText());
        academies.save(a);
        List<AcademyService.Video> showcase = new ArrayList<>();
        int n = 1;
        for (JsonNode c : t.path("courses")) {
            String file = "/videos/demo-" + t.path("key").asText() + "-" + n++;
            showcase.add(new AcademyService.Video(c.path("title").asText(), c.path("description").asText(), file + ".mp4", file + ".jpg", c.path("year").asText()));
        }
        return academyService.save(actor, a.getId(), new AcademyService.Content(t.path("name").asText(), t.path("tagline").asText(),
                t.path("headline").asText(), t.path("description").asText(), t.path("about").asText(), t.path("subject").asText(),
                "", false, true, showcase, "", "", ""));
    }

    private int createCourses(TeacherAcademy a, JsonNode t) {
        Long branchId = users.findById(a.getTeacherId()).map(u -> u.getBranchId()).orElseThrow();
        int n = 1;
        for (JsonNode c : t.path("courses")) {
            String file = "/videos/demo-" + t.path("key").asText() + "-" + n++;
            Course course = new Course();
            course.setTenantId(a.getTenantId()); course.setBranchId(branchId); course.setTeacherId(a.getTeacherId());
            course.setTitle(c.path("title").asText()); course.setSubject(t.path("subject").asText());
            course.setGradeLevel(c.path("level").asText()); course.setGrade(c.path("year").asText());
            course.setDescription(c.path("description").asText()); course.setPrice(new BigDecimal(c.path("price").asText()));
            course.setStatus("ACTIVE"); course.setCoverUrl(file + ".jpg");
            courses.save(course);

            CourseModule module = new CourseModule();
            module.setTenantId(a.getTenantId()); module.setCourseId(course.getId()); module.setTitle("المحاضرة الأولى"); module.setPosition(1);
            modules.save(module);

            Lesson lesson = new Lesson();
            lesson.setTenantId(a.getTenantId()); lesson.setModuleId(module.getId()); lesson.setPosition(1);
            lesson.setTitle("مقدمة: " + c.path("title").asText()); lesson.setDurationMin(c.path("minutes").asInt(12));
            StringBuilder text = new StringBuilder(c.path("description").asText()).append("\n\nهنتكلم في الدرس ده عن:");
            for (JsonNode p : c.path("points")) text.append("\n• ").append(p.asText());
            lesson.setContentText(text.toString());
            lessons.save(lesson);

            LessonMaterial video = new LessonMaterial();
            video.setTenantId(a.getTenantId()); video.setLessonId(lesson.getId()); video.setType("VIDEO");
            video.setTitle("فيديو تجريبي — " + c.path("title").asText()); video.setUrl(file + ".mp4"); video.setDurationSec(7);
            materials.save(video);
        }
        return t.path("courses").size();
    }

    private static JsonNode load() {
        try (var in = new ClassPathResource("demo/package-content.json").getInputStream()) {
            return new ObjectMapper().readTree(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Demo package content is missing", e);
        }
    }
}
