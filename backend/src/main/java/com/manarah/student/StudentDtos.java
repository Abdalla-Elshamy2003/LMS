package com.manarah.student;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public class StudentDtos {

    public record StudentSummary(
            Long id, String code, String fullName, String grade, String gradeLevel,
            String status, String academicStatus, double avgScore, double attendanceRate,
            double homeworkRate, double overallPercent, Long branchId, String phone, String email) {
    }

    public record GuardianView(Long id, String fullName, String phone, String email, String relation, Long userId) {
    }

    public record EnrollmentView(Long id, Long courseId, String courseTitle, String status) {
    }

    public record RiskView(String level, double score, List<String> reasons, String assessedAt) {
    }

    public record StudentDetail(
            StudentSummary summary,
            String nationalId, LocalDate birthDate, String gender, String school, String notes,
            List<GuardianView> guardians,
            List<EnrollmentView> enrollments,
            RiskView risk) {
    }

    public record CreateStudentRequest(
            @NotBlank String fullName,
            String code,
            String nationalId,
            LocalDate birthDate,
            String gender,
            String gradeLevel,
            String grade,
            String school,
            String phone,
            Long branchId,
            String notes) {
    }

    public record UpdateStudentRequest(
            String fullName, String status, String gradeLevel, String grade,
            String school, String phone, String notes, Long branchId) {
    }

    public record CreateGuardianRequest(
            @NotBlank String fullName, String phone, String email, String relation, boolean createLogin) {
    }
}
