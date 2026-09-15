package com.manarah.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.course.repo.CourseRepository;
import com.manarah.course.repo.CourseModuleRepository;
import com.manarah.course.repo.LessonMaterialRepository;
import com.manarah.course.repo.LessonRepository;
import com.manarah.enrollment.domain.Enrollment;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.exam.domain.Exam;
import com.manarah.exam.repo.ExamRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import jakarta.servlet.http.Cookie;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123",
        "manarah.security.cors.allowed-origins=http://localhost:5173",
        "manarah.whatsapp.app-secret=test-meta-app-secret",
        "manarah.whatsapp.verify-token=test-meta-verify-token"
})
@AutoConfigureMockMvc
class SecurityRegressionTest {
    private static final String RUN = "security-test-" + UUID.randomUUID();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> "jdbc:sqlite:" + Path.of("target", RUN + ".db").toAbsolutePath() + "?foreign_keys=true&date_class=text&busy_timeout=5000");
        props.add("manarah.storage.root", () -> Path.of("target", RUN + "-files").toAbsolutePath().toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired CourseRepository courses;
    @Autowired CourseModuleRepository modules;
    @Autowired LessonRepository lessons;
    @Autowired LessonMaterialRepository materials;
    @Autowired EnrollmentRepository enrollments;
    @Autowired ExamRepository exams;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired com.manarah.payment.repo.CoursePurchaseOrderRepository purchaseOrders;

    private record Session(String token, String cookie) {}

    Session login(String email) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "manarah123"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode body = json.readTree(response.getContentAsString());
        return new Session(body.get("accessToken").asText(), response.getHeader("Set-Cookie"));
    }

    @Test
    void protectedRoutesRejectAnonymousQueryTokensAndUntrustedOrigins() throws Exception {
        String student = login("student@manarah.io").token();
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").param("token", student)).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/landing").header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/public/landing").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void whatsappWebhookRequiresMetaSignatureAndConfiguredHandshakeToken() throws Exception {
        byte[] payload = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        mvc.perform(get("/api/whatsapp/webhook")
                        .param("hub.mode", "subscribe").param("hub.verify_token", "wrong")
                        .param("hub.challenge", "challenge"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/whatsapp/webhook")
                        .param("hub.mode", "subscribe").param("hub.verify_token", "test-meta-verify-token")
                        .param("hub.challenge", "challenge"))
                .andExpect(status().isOk()).andExpect(content().string("challenge"));
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Hub-Signature-256", "sha256=invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Hub-Signature-256", metaSignature(payload)))
                .andExpect(status().isOk());
    }

    private static String metaSignature(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-meta-app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }

    @Test
    void loginCookieIsHttpOnlyScopedAndDisabledUsersLoseExistingTokens() throws Exception {
        Session session = login("student@manarah.io");
        assertThat(session.cookie()).contains("HttpOnly", "SameSite=Strict", "Path=/api/files")
                .doesNotContain("student@manarah.io");
        User user = users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        try {
            user.setStatus("INACTIVE"); users.saveAndFlush(user);
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + session.token()))
                    .andExpect(status().isUnauthorized());
        } finally {
            user.setStatus("ACTIVE"); users.saveAndFlush(user);
        }
    }

    @Test
    void bruteForceIsRateLimitedWithoutAccountEnumeration() throws Exception {
        String body = "{\"email\":\"nobody-security-test@example.com\",\"password\":\"wrong-password-123\"}";
        for (int i = 0; i < 8; i++) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("بيانات الدخول غير صحيحة"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void uploadsAndCourseRostersAreRoleScoped() throws Exception {
        String student = login("student@manarah.io").token();
        long courseId = enrollments.findByTenantIdAndStudentId(
                users.findByEmailIgnoreCase("student@manarah.io").orElseThrow().getTenantId(),
                students.findByTenantIdAndUserId(users.findByEmailIgnoreCase("student@manarah.io").orElseThrow().getTenantId(),
                        users.findByEmailIgnoreCase("student@manarah.io").orElseThrow().getId()).orElseThrow().getId()).getFirst().getCourseId();
        var video = new MockMultipartFile("file", "lesson.mp4", "video/mp4", new byte[]{0, 1, 2});
        mvc.perform(multipart("/api/files/upload").file(video).param("folder", "materials")
                        .header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/enrollments/course/" + courseId).header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullBearerTokensNeverAppearInFileUrlsAndOrphanFilesStayPrivate() throws Exception {
        Session student = login("student@manarah.io");
        String teacher = login("teacher@manarah.io").token();
        var uploadResponse = mvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "notes.pdf", "application/pdf", "%PDF-test".getBytes()))
                        .param("folder", "materials").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String key = json.readTree(uploadResponse).get("fileKey").asText();
        mvc.perform(get("/api/files/" + key).param("token", student.token()))
                .andExpect(status().isUnauthorized());
        String cookieValue = student.cookie().split(";", 2)[0].split("=", 2)[1];
        mvc.perform(get("/api/files/" + key).cookie(new Cookie(FileSessionCookie.COOKIE_NAME, cookieValue)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teachersCannotModifyAnotherTeachersCourse() throws Exception {
        String owner = login("teacher@manarah.io").token();
        String other = login("sara@manarah.io").token();
        long courseId = json.readTree(mvc.perform(post("/api/courses").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"ملكية المحتوى\",\"subject\":\"رياضيات\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("summary").path("id").asLong();
        mvc.perform(post("/api/courses/" + courseId + "/modules").header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"اختراق\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aStudentCannotSubmitAnotherStudentsExamAttempt() throws Exception {
        User firstUser = users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        Student first = students.findByTenantIdAndUserId(firstUser.getTenantId(), firstUser.getId()).orElseThrow();
        Enrollment firstEnrollment = enrollments.findByTenantIdAndStudentId(firstUser.getTenantId(), first.getId()).getFirst();

        Exam exam = new Exam(); exam.setTenantId(firstUser.getTenantId()); exam.setCourseId(firstEnrollment.getCourseId());
        exam.setTitle("اختبار ملكية المحاولة"); exam.setStatus("PUBLISHED"); exam.setDurationMinutes(30); exams.save(exam);
        String firstToken = jwt.generateAccessToken(firstUser);
        long attemptId = json.readTree(mvc.perform(post("/api/exams/" + exam.getId() + "/start")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("studentExamId").asLong();

        User secondUser = new User(); secondUser.setTenantId(firstUser.getTenantId()); secondUser.setBranchId(firstUser.getBranchId());
        secondUser.setFullName("طالب اختبار أمني"); secondUser.setEmail("security-second-" + RUN + "@example.com");
        secondUser.setRole(Role.STUDENT); secondUser.setPasswordHash(passwords.encode("StudentPass123")); users.save(secondUser);
        Student second = new Student(); second.setTenantId(firstUser.getTenantId()); second.setBranchId(firstUser.getBranchId());
        second.setUserId(secondUser.getId()); second.setFullName(secondUser.getFullName()); second.setCode("SEC-" + UUID.randomUUID());
        second.setStatus("ACTIVE"); students.save(second);
        Enrollment secondEnrollment = new Enrollment(); secondEnrollment.setTenantId(firstUser.getTenantId());
        secondEnrollment.setStudentId(second.getId()); secondEnrollment.setCourseId(firstEnrollment.getCourseId()); enrollments.save(secondEnrollment);

        mvc.perform(post("/api/exams/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + jwt.generateAccessToken(secondUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[],\"tabSwitches\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void criticalRoleDashboardsAndWorkspacesLoadEndToEnd() throws Exception {
        String admin = login("admin@manarah.io").token();
        String teacher = login("teacher@manarah.io").token();
        String student = login("student@manarah.io").token();
        String parent = login("parent@manarah.io").token();
        User studentUser = users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        long studentId = students.findByTenantIdAndUserId(studentUser.getTenantId(), studentUser.getId()).orElseThrow().getId();

        assertOk(admin, List.of("/api/dashboard/admin", "/api/students?size=5", "/api/courses", "/api/exams",
                "/api/homework/assignments", "/api/payments/invoices", "/api/org/branches", "/api/users",
                "/api/reports/academy", "/api/calendar", "/api/schedule", "/api/notifications"));
        assertOk(teacher, List.of("/api/dashboard/teacher", "/api/courses", "/api/learning", "/api/exams",
                "/api/homework/assignments", "/api/reports/academy", "/api/schedule", "/api/notifications"));
        assertOk(student, List.of("/api/dashboard/me", "/api/students/me", "/api/courses", "/api/learning",
                "/api/homework/my", "/api/attendance/my", "/api/attendance/my-courses",
                "/api/exams/student/" + studentId, "/api/reports/me", "/api/gradebook/student/" + studentId,
                "/api/schedule", "/api/notifications"));
        assertOk(parent, List.of("/api/dashboard/parent", "/api/students/children", "/api/family/finance",
                "/api/calendar", "/api/notifications"));
    }

    private void assertOk(String token, List<String> paths) throws Exception {
        for (String path : paths) {
            int status = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(path).isBetween(200, 299);
        }
    }

    @Test void unknownAndMalformedRequestsHaveSafeClientErrors() throws Exception {
        String admin = login("admin@manarah.io").token();
        mvc.perform(get("/api/not-a-real-route").header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses").header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON).content("{broken")).andExpect(status().isBadRequest());
    }

    @Test void productionCannotConfirmSandboxPayments() throws Exception {
        User user = users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        Student student = students.findByTenantIdAndUserId(user.getTenantId(), user.getId()).orElseThrow();
        var enrollment = enrollments.findByTenantIdAndStudentId(user.getTenantId(), student.getId()).getFirst();
        var order = new com.manarah.payment.domain.CoursePurchaseOrder();
        order.setTenantId(user.getTenantId()); order.setUserId(user.getId()); order.setStudentId(student.getId());
        order.setCourseId(enrollment.getCourseId()); order.setReference("TEST-" + UUID.randomUUID());
        order.setAmount(new java.math.BigDecimal("100")); purchaseOrders.saveAndFlush(order);
        mvc.perform(post("/api/checkout/orders/" + order.getReference() + "/pay")
            .header("Authorization", "Bearer " + jwt.generateAccessToken(user)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\":\"CARD\"}")).andExpect(status().isForbidden());
        assertThat(purchaseOrders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test void studentExamCatalogIsFilteredAndNeverLeaksAnswerKeys() throws Exception {
        User user = users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        Student student = students.findByTenantIdAndUserId(user.getTenantId(), user.getId()).orElseThrow();
        var enrollment = enrollments.findByTenantIdAndStudentId(user.getTenantId(), student.getId()).getFirst();
        String teacher = login("admin@manarah.io").token(), token = jwt.generateAccessToken(user);
        JsonNode question = json.readTree(mvc.perform(post("/api/exams/questions").header("Authorization", "Bearer " + teacher)
            .contentType(MediaType.APPLICATION_JSON).content("{\"difficulty\":\"EASY\",\"type\":\"MCQ\",\"stem\":\"2+3?\",\"points\":10,\"options\":[{\"text\":\"5\",\"correct\":true},{\"text\":\"6\",\"correct\":false}]}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Exam ready = new Exam(); ready.setTenantId(user.getTenantId()); ready.setCourseId(enrollment.getCourseId());
        ready.setTitle("Catalog regression"); ready.setDurationMinutes(30); ready.setStatus("DRAFT"); exams.saveAndFlush(ready);
        mvc.perform(post("/api/exams/" + ready.getId() + "/questions").header("Authorization", "Bearer " + teacher)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("questionId", question.path("id").asLong())))).andExpect(status().isOk());
        ready = exams.findById(ready.getId()).orElseThrow(); ready.setStatus("PUBLISHED"); exams.saveAndFlush(ready);
        Exam future = new Exam(); future.setTenantId(user.getTenantId()); future.setCourseId(enrollment.getCourseId());
        future.setTitle("Future regression"); future.setStatus("PUBLISHED"); future.setStartAt(java.time.Instant.now().plusSeconds(3600)); exams.saveAndFlush(future);
        Exam draft = new Exam(); draft.setTenantId(user.getTenantId()); draft.setCourseId(enrollment.getCourseId());
        draft.setTitle("Draft regression"); draft.setStatus("DRAFT"); exams.saveAndFlush(draft);
        String catalog = mvc.perform(get("/api/exams").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(catalog).contains("Catalog regression").doesNotContain("Future regression", "Draft regression", "correctAnswer", "\"correct\"", "2+3?");
        mvc.perform(get("/api/exams/questions").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/exams/" + ready.getId()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        String attemptBody = mvc.perform(post("/api/exams/" + ready.getId() + "/start").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(attemptBody).doesNotContain("correctAnswer", "\"correct\"");
        long attempt = json.readTree(attemptBody).path("studentExamId").asLong();
        long correct = question.path("options").get(0).path("id").asLong();
        String answers = json.writeValueAsString(Map.of("answers", List.of(Map.of("questionId", question.path("id").asLong(), "selectedOptions", List.of(correct)))));
        mvc.perform(post("/api/exams/attempts/" + attempt + "/submit").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(answers))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(10)).andExpect(jsonPath("$.maxScore").value(10));
        mvc.perform(post("/api/exams/attempts/" + attempt + "/submit").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(answers)).andExpect(status().isOk()).andExpect(jsonPath("$.score").value(10));
    }

    @Test
    void demoCatalogContainsFiveTeachersWithCoursesAndStudents() {
        User admin = users.findByEmailIgnoreCase("admin@manarah.io").orElseThrow();
        var teachers = users.findByTenantIdAndRole(admin.getTenantId(), Role.TEACHER);
        assertThat(teachers).hasSizeGreaterThanOrEqualTo(5);
        teachers.forEach(teacher -> {
            var taught = courses.findByTenantIdAndTeacherId(admin.getTenantId(), teacher.getId());
            assertThat(taught).isNotEmpty();
            long learners = taught.stream().mapToLong(course ->
                    enrollments.countByTenantIdAndCourseId(admin.getTenantId(), course.getId())).sum();
            assertThat(learners).isPositive();
            boolean hasPreviewableVideo = taught.stream()
                    .flatMap(course -> modules.findByTenantIdAndCourseIdOrderByPosition(admin.getTenantId(), course.getId()).stream())
                    .flatMap(module -> lessons.findByTenantIdAndModuleIdOrderByPosition(admin.getTenantId(), module.getId()).stream())
                    .filter(lesson -> lesson.getContentText() != null && !lesson.getContentText().isBlank())
                    .anyMatch(lesson -> materials.findByTenantIdAndLessonId(admin.getTenantId(), lesson.getId()).stream()
                            .anyMatch(material -> "VIDEO".equals(material.getType())
                                    && ((material.getUrl() != null && !material.getUrl().isBlank())
                                    || (material.getFileKey() != null && !material.getFileKey().isBlank()))));
            assertThat(hasPreviewableVideo).as(teacher.getEmail()).isTrue();
        });
    }
}
