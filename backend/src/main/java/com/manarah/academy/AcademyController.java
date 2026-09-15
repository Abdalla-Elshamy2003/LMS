package com.manarah.academy;

import com.manarah.security.UserPrincipal;
import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.repo.CourseRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import javax.imageio.ImageIO;

@RestController @RequestMapping("/api")
public class AcademyController {
    private final AcademyService service;
    private final TeacherAcademyRepository academies;
    private final CourseRepository courses;
    private final com.manarah.student.repo.StudentRepository students;
    public AcademyController(AcademyService service, TeacherAcademyRepository academies, CourseRepository courses,
                             com.manarah.student.repo.StudentRepository students) {
        this.service = service; this.academies = academies; this.courses = courses; this.students = students;
    }

    /** Public home page payload: platform-wide numbers plus the teacher cards. */
    @GetMapping("/public/home")
    public Object home() {
        var published = academies.findByPublishedTrueOrderByNameAsc();
        long courseCount = 0, studentCount = 0;
        for (var a : published) {
            courseCount += courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
            studentCount += students.countByTenantId(a.getTenantId());
        }
        return Map.of("stats", Map.of("teachers", published.size(), "courses", courseCount, "students", studentCount),
                "teachers", directory());
    }
    @GetMapping("/academies") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER','TEACHER')")
    public Object list(@AuthenticationPrincipal UserPrincipal actor) { return service.list(actor); }
    @GetMapping("/academy-context")
    public Object context(@AuthenticationPrincipal UserPrincipal actor) {
        return academies.findByTenantId(actor.getTenantId()).map(a -> Map.<String,Object>of("id", a.getId(), "name", a.getName(), "slug", a.getSlug(), "teacherId", a.getTeacherId())).orElse(Map.of());
    }
    @PostMapping("/academies") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody AcademyService.CreateAcademy req) { return service.create(actor, req); }
    @PutMapping("/academies/{id}")
    public Object save(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody AcademyService.Content req) { return service.save(actor, id, req); }
    public record OwnerBody(Long userId) {}

    /** Links (or unlinks, with a null userId) a school teacher account to this page. */
    @PutMapping("/academies/{id}/owner") @PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACADEMIC_MANAGER')")
    public Object owner(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody OwnerBody body) {
        return service.assignOwner(actor, id, body.userId());
    }
    @PutMapping("/academies/{id}/home")
    public void home(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { service.home(actor, id); }
    @PutMapping("/academies/{id}/credentials")
    public void credentials(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody AcademyService.Credentials req) { service.teacherCredentials(actor, id, req); }
    @GetMapping("/academies/{id}/courses")
    public Object courses(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) {
        var a = service.manage(actor, id); return courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId());
    }
    @GetMapping("/academies/{id}/students")
    public Object students(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.accounts(actor, id); }
    @PostMapping("/academies/{id}/students")
    public Object createStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @RequestBody AcademyService.Account req) { return service.createStudent(actor, id, req); }
    @PutMapping("/academies/{id}/students/{studentId}")
    public void access(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable Long studentId, @RequestBody AcademyService.Account req) { service.access(actor, id, studentId, req); }
    @DeleteMapping("/academies/{id}/students/{studentId}")
    public void removeStudent(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable Long studentId) { service.removeStudent(actor, id, studentId); }
    @PostMapping("/academies/{id}/images/{slot}") @Transactional
    public Object upload(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id, @PathVariable String slot, @RequestParam MultipartFile file) throws Exception {
        var a = service.manage(actor, id);
        if (!Set.of("photo", "cover").contains(slot)) throw new BadRequestException("مكان الصورة غير صالح");
        if (file.isEmpty() || file.getSize() > 3 * 1024 * 1024) throw new BadRequestException("اختر صورة أصغر من 3 ميجابايت");
        String type = Objects.toString(file.getContentType(), "");
        if (!Set.of("image/png", "image/jpeg").contains(type)) throw new BadRequestException("الصورة يجب أن تكون PNG أو JPG");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("ملف الصورة غير صالح");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if ((long)reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw new BadRequestException("أبعاد الصورة كبيرة جداً");
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new BadRequestException("صيغة الصورة غير صالحة");
                type = format.equals("png") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        }
        String data = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
        if (slot.equals("photo")) { a.setPhotoData(data); a.setPhotoUrl("/api/public/academies/" + a.getSlug() + "/images/photo?v=" + System.currentTimeMillis()); }
        else a.setCoverData(data);
        academies.save(a); return Map.of("url", slot.equals("photo") ? a.getPhotoUrl() : "/api/public/academies/" + a.getSlug() + "/images/cover?v=" + System.currentTimeMillis());
    }
    private TeacherAcademy published(String slug) {
        return (slug.equals("default") ? academies.findByDefaultHomeTrue() : academies.findBySlug(slug))
            .filter(TeacherAcademy::isPublished).orElseThrow(() -> new NotFoundException("صفحة المدرس غير متاحة"));
    }
    /** The public directory: every published teacher page, as a card. */
    @GetMapping("/public/academies")
    public Object directory() {
        return academies.findByPublishedTrueOrderByNameAsc().stream().map(a -> {
            var taught = courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream()
                    .filter(c -> "ACTIVE".equals(c.getStatus())).toList();
            return Map.<String,Object>of("slug", a.getSlug(), "name", a.getName(), "tagline", Objects.toString(a.getTagline(), ""),
                    "subject", Objects.toString(a.getSubject(), ""), "headline", Objects.toString(a.getHeadline(), ""),
                    "photoUrl", Objects.toString(a.getPhotoUrl(), ""), "courseCount", taught.size(),
                    "grades", taught.stream().map(c -> Objects.toString(c.getGradeLevel(), "")).filter(g -> !g.isBlank()).distinct().toList());
        }).toList();
    }
    @GetMapping("/public/academies/{slug}")
    public Object landing(@PathVariable String slug) {
        var a = published(slug);
        return Map.of("profile", a, "coverUrl", a.getCoverData() == null ? "" : "/api/public/academies/" + a.getSlug() + "/images/cover",
            // videos are whatever the teacher/admin saved from the academy settings page — the
            // landing page shows them in the same slider as the courses, so publishing either one
            // is enough to make it appear publicly.
            "videos", a.getVideos(),
            "courses", courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream().filter(c -> "ACTIVE".equals(c.getStatus()))
                .map(c -> Map.<String,Object>of("id", c.getId(), "title", c.getTitle(), "grade", Objects.toString(c.getGradeLevel(), ""),
                    "year", Objects.toString(c.getGrade(), ""), "coverUrl", Objects.toString(c.getCoverUrl(), ""),
                    "description", Objects.toString(c.getDescription(), ""),
                    "price", c.getPrice(), "finalPrice", c.getFinalPrice(), "discountPercent", c.getDiscountPercent() == null ? 0 : c.getDiscountPercent())).toList());
    }
    @GetMapping("/public/academies/{slug}/images/{slot}")
    public ResponseEntity<byte[]> image(@PathVariable String slug, @PathVariable String slot) {
        var a = published(slug); String data = slot.equals("photo") ? a.getPhotoData() : slot.equals("cover") ? a.getCoverData() : null;
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff").contentType(MediaType.parseMediaType(data.substring(5, data.indexOf(';'))))
            .body(Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1)));
    }
}
