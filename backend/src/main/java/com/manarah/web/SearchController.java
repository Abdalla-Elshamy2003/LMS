package com.manarah.web;

import com.manarah.common.tenant.TenantContext;
import com.manarah.course.repo.CourseRepository;
import com.manarah.exam.repo.ExamRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.student.repo.StudentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Unified search across the main entities (students, courses, teachers, exams). */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search")
public class SearchController {

    private final StudentRepository students;
    private final CourseRepository courses;
    private final UserRepository users;
    private final ExamRepository exams;
    private final com.manarah.academy.AcademyAccess access;

    public SearchController(StudentRepository students, CourseRepository courses, UserRepository users, ExamRepository exams, com.manarah.academy.AcademyAccess access) {
        this.students = students;
        this.courses = courses;
        this.users = users;
        this.exams = exams; this.access = access;
    }

    @GetMapping
    public Map<String, Object> search(@org.springframework.security.core.annotation.AuthenticationPrincipal com.manarah.security.UserPrincipal actor, @RequestParam String q) {
        Long tenantId = TenantContext.require();
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();
        if (needle.length() < 1) return Map.of("results", results);

        if (actor.isStaff()) students.search(tenantId, null, null, null, q, PageRequest.of(0, 6)).forEach(s ->
                results.add(hit("student", s.getId(), s.getFullName(), s.getCode() + " · " + (s.getGrade() == null ? "" : s.getGrade()), "/app/students/" + s.getId())));

        courses.findByTenantId(tenantId).stream().filter(c -> access.visible(c.getId()))
                .filter(c -> contains(c.getTitle(), needle) || contains(c.getSubject(), needle))
                .limit(6).forEach(c ->
                        results.add(hit("course", c.getId(), c.getTitle(), c.getSubject(), "/app/courses/" + c.getId())));

        if (actor.isAdmin()) users.findByTenantIdAndRole(tenantId, Role.TEACHER).stream()
                .filter(u -> contains(u.getFullName(), needle) || contains(u.getSubjects(), needle))
                .limit(6).forEach(u ->
                        results.add(hit("teacher", u.getId(), u.getFullName(), u.getSubjects(), "/app/staff")));

        exams.findByTenantId(tenantId).stream().filter(e -> access.visible(e.getCourseId()))
                .filter(e -> contains(e.getTitle(), needle))
                .limit(6).forEach(e ->
                        results.add(hit("exam", e.getId(), e.getTitle(), "امتحان", "/app/exams")));

        return Map.of("results", results);
    }

    private static boolean contains(String v, String needle) {
        return v != null && v.toLowerCase().contains(needle);
    }

    private static Map<String, Object> hit(String type, Long id, String title, String subtitle, String href) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("title", title);
        m.put("subtitle", subtitle == null ? "" : subtitle);
        m.put("href", href);
        return m;
    }
}
