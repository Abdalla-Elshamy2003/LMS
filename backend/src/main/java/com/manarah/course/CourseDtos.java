package com.manarah.course;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class CourseDtos {

    public record CourseSummary(
            Long id, String title, String subject, String gradeLevel, String status,
            BigDecimal price, Long teacherId, String teacherName, long studentCount, String coverUrl,
            String schedule, String grade, Integer discountPercent, BigDecimal finalPrice, Long academyId) {
    }

    public record SetDiscountRequest(Integer discountPercent) {
    }

    public record MaterialView(Long id, String type, String title, String description, String url, String fileKey,
                               Long sizeBytes, Integer durationSec, Instant createdAt) {
    }

    public record LessonView(Long id, String title, int position, int durationMin, String contentText,
                             List<MaterialView> materials, Instant createdAt, Instant releaseAt, String aiSummary) {
    }

    public record ModuleView(Long id, String title, int position, List<LessonView> lessons, Instant createdAt) {
    }

    public record CourseDetail(CourseSummary summary, String description, List<ModuleView> modules) {
    }

    public record CreateCourseRequest(
            @NotBlank String title, String subject, String gradeLevel, String description,
            BigDecimal price, Long teacherId, Long branchId, String coverUrl, String schedule, String grade) {
    }

    public record CreateModuleRequest(@NotBlank String title, Integer position) {
    }

    public record UpdateModuleRequest(@NotBlank String title) {
    }

    public record UpdateLessonRequest(@NotBlank String title, Integer durationMin, String contentText, Instant releaseAt) {
    }

    public record CreateLessonRequest(@NotBlank String title, Integer position, Integer durationMin, String contentText,
                                      Instant releaseAt) {
    }

    public record CreateMaterialRequest(@NotBlank String type, @NotBlank String title, String description, String url,
                                        String fileKey, Long sizeBytes, Integer durationSec) {
    }
}
