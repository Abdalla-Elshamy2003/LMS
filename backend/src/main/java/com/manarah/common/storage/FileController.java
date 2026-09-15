package com.manarah.common.storage;

import com.manarah.common.exception.ApiExceptions.ForbiddenException;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.UnauthorizedException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.course.LearningService;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import com.manarah.identity.domain.Role;
import com.manarah.homework.repo.AssignmentRepository;
import com.manarah.homework.repo.SubmissionRepository;
import com.manarah.exam.repo.ExamRepository;
import com.manarah.student.StudentAccessPolicy;

/** Upload/download for lesson materials and homework files (§7). Files live on disk, not in SQLite. */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Files")
public class FileController {

    private final FileStorage storage;
    private final LessonMaterialRepository materials;
    private final LearningService learning;
    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final ExamRepository exams;
    private final StudentAccessPolicy studentAccess;
    private final UploadOwnership uploadOwnership;
    private final com.manarah.homework.repo.SubmissionFileRepository submissionFiles;
    private final com.manarah.exam.repo.QuestionRepository questions;
    private final com.manarah.exam.repo.ExamQuestionRepository examQuestions;
    private final com.manarah.course.repo.LessonCheckpointRepository checkpoints;

    public FileController(FileStorage storage, LessonMaterialRepository materials, LearningService learning,
                          AssignmentRepository assignments, SubmissionRepository submissions,
                          ExamRepository exams, StudentAccessPolicy studentAccess, UploadOwnership uploadOwnership,
                          com.manarah.homework.repo.SubmissionFileRepository submissionFiles,
                          com.manarah.exam.repo.QuestionRepository questions,
                          com.manarah.exam.repo.ExamQuestionRepository examQuestions,
                          com.manarah.course.repo.LessonCheckpointRepository checkpoints) {
        this.questions = questions;
        this.examQuestions = examQuestions;
        this.checkpoints = checkpoints;
        this.submissionFiles = submissionFiles;
        this.storage = storage;
        this.materials = materials;
        this.learning = learning;
        this.assignments = assignments;
        this.submissions = submissions;
        this.exams = exams;
        this.studentAccess = studentAccess;
        this.uploadOwnership = uploadOwnership;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@AuthenticationPrincipal UserPrincipal actor,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "materials") String folder) {
        String cleanFolder = folder == null ? "" : folder.toLowerCase(Locale.ROOT).trim();
        validateUpload(actor, cleanFolder, file);
        String key = storage.store(TenantContext.require(), cleanFolder, file);
        uploadOwnership.record(actor, key);
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        return Map.of("fileKey", key, "name", originalName, "size", file.getSize());
    }

    @GetMapping("/{*key}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal actor, @PathVariable String key) {
        if (actor == null) throw new UnauthorizedException("يلزم تسجيل الدخول للوصول إلى الملف");
        String clean = key.startsWith("/") ? key.substring(1) : key;
        // Enforce tenant ownership + prevent path traversal: the resolved file must live inside
        // this tenant's own subtree (t{tenantId}/...), regardless of any "../" in the key.
        Long tenantId = TenantContext.require();
        Path tenantRoot = storage.resolve("t" + tenantId).toAbsolutePath().normalize();
        Path target = storage.resolve(clean).toAbsolutePath().normalize();
        if (!target.startsWith(tenantRoot)) {
            throw new ForbiddenException("لا يمكن الوصول إلى هذا الملف");
        }
        authorizeDownload(actor, tenantId, clean);
        if (clean.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|mov|m4v)$"))
            throw new ForbiddenException("استخدم مشغّل الدرس لمشاهدة الفيديو");
        Resource resource = new FileSystemResource(target);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String filename = target.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(resource);
    }

    private void validateUpload(UserPrincipal actor, String folder, MultipartFile file) {
        if (actor == null) throw new UnauthorizedException("يلزم تسجيل الدخول لرفع الملفات");
        Role role = actor.getRole();
        boolean teachingStaff = actor.isAdmin() || Set.of(Role.ACADEMIC_MANAGER, Role.TEACHER,
                Role.ASSISTANT, Role.CONTENT_MANAGER).contains(role);
        if ("submissions".equals(folder)) {
            if (!(role == Role.STUDENT || teachingStaff)) throw new ForbiddenException("غير مسموح برفع تسليمات");
            validateFile(file, 25L * 1024 * 1024, Set.of("pdf", "doc", "docx", "png", "jpg", "jpeg", "webp", "zip"));
            return;
        }
        if (Set.of("materials", "assignments", "exams").contains(folder)) {
            if (!teachingStaff) throw new ForbiddenException("رفع محتوى الكورس متاح لفريق التدريس فقط");
            long max = "materials".equals(folder) ? 512L * 1024 * 1024 : 25L * 1024 * 1024;
            Set<String> extensions = "materials".equals(folder)
                    ? Set.of("mp4", "webm", "mov", "m4v", "pdf", "ppt", "pptx", "doc", "docx", "png", "jpg", "jpeg", "webp", "mp3", "wav", "m4a")
                    : Set.of("pdf", "doc", "docx", "ppt", "pptx", "png", "jpg", "jpeg", "webp");
            validateFile(file, max, extensions);
            return;
        }
        throw new BadRequestException("نوع الرفع غير مدعوم");
    }

    private void validateFile(MultipartFile file, long maxBytes, Set<String> extensions) {
        if (file == null || file.isEmpty()) throw new BadRequestException("الملف فارغ");
        if (file.getSize() > maxBytes) throw new BadRequestException("حجم الملف أكبر من الحد المسموح");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extensions.contains(ext)) throw new BadRequestException("امتداد الملف غير مسموح");
    }

    private void authorizeDownload(UserPrincipal actor, Long tenantId, String key) {
        var material = materials.findByTenantIdAndFileKey(tenantId, key);
        if (material.isPresent()) {
            if ("VIDEO".equals(material.get().getType())) throw new ForbiddenException("استخدم مشغّل الدرس لمشاهدة الفيديو");
            learning.access(actor, learning.lessonCourse(actor, material.get().getLessonId()), false);
            learning.requireReleasedLesson(actor, material.get().getLessonId());
            return;
        }
        var assignment = assignments.findByTenantIdAndFileKey(tenantId, key);
        if (assignment.isPresent()) {
            if (!actor.isStaff() && assignment.get().getStartAt() != null && Instant.now().isBefore(assignment.get().getStartAt()))
                throw new ForbiddenException("لم يفتح الواجب بعد");
            learning.access(actor, assignment.get().getCourseId(), false);
            return;
        }
        var exam = exams.findByTenantIdAndPdfKey(tenantId, key);
        if (exam.isPresent()) {
            var e = exam.get();
            if (!actor.isStaff()) {
                Instant now = Instant.now();
                if (!"PUBLISHED".equals(e.getStatus()) || (e.getStartAt() != null && now.isBefore(e.getStartAt()))
                        || (e.getEndAt() != null && now.isAfter(e.getEndAt()))) {
                    throw new ForbiddenException("الامتحان غير متاح الآن");
                }
            }
            if (e.getCourseId() == null) {
                if (!actor.isStaff()) throw new ForbiddenException("لا يمكن الوصول إلى هذا الملف");
            } else learning.access(actor, e.getCourseId(), false);
            return;
        }
        var questionImage = questions.findByTenantIdAndImageKey(tenantId, key);
        if (questionImage.isPresent()) {
            if (actor.isStaff()) return;
            // A student may load the picture only for a question actually put in front of them —
            // in an exam or a lesson checkpoint whose course they can open.
            Long questionId = questionImage.get().getId();
            boolean reachable = examQuestions.findByTenantIdAndQuestionId(tenantId, questionId).stream()
                    .map(eq -> exams.findByTenantIdAndId(tenantId, eq.getExamId()).map(e -> e.getCourseId()).orElse(null))
                    .anyMatch(courseId -> courseId != null && canOpen(actor, courseId));
            if (!reachable) reachable = checkpoints.findByTenantIdAndQuestionId(tenantId, questionId).stream()
                    .anyMatch(c -> {
                        try { return canOpen(actor, learning.lessonCourse(actor, c.getLessonId())); }
                        catch (com.manarah.common.exception.ApiExceptions.ApiException e) { return false; }
                    });
            if (!reachable) throw new ForbiddenException("لا يمكن الوصول إلى هذا الملف");
            return;
        }
        var submission = submissions.findByTenantIdAndFileKey(tenantId, key)
                .or(() -> submissionFiles.findByTenantIdAndFileKey(tenantId, key)
                        .flatMap(f -> submissions.findById(f.getSubmissionId()).filter(s -> tenantId.equals(s.getTenantId()))));
        if (submission.isPresent()) {
            var s = submission.get();
            var a = assignments.findByTenantIdAndId(tenantId, s.getAssignmentId())
                    .orElseThrow(() -> new ForbiddenException("لا يمكن الوصول إلى هذا الملف"));
            if (actor.getRole() == Role.STUDENT || actor.getRole() == Role.PARENT) {
                studentAccess.assertCanView(actor, s.getStudentId());
            } else {
                learning.access(actor, a.getCourseId(), true);
            }
            return;
        }
        // Orphaned/unlinked uploads must never become public merely because someone guessed a key.
        throw new ForbiddenException("لا يمكن الوصول إلى هذا الملف");
    }

    private boolean canOpen(UserPrincipal actor, Long courseId) {
        try { learning.access(actor, courseId, false); return true; }
        catch (com.manarah.common.exception.ApiExceptions.ApiException e) { return false; }
    }
}
