package com.manarah.course;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * One student, many teachers: one sign-in, a seat in each teacher's space, switching between them, a second
 * teacher's code opening that teacher's course, and one QR that records attendance with whoever scanned it.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class LinkedStudentAccountsTest {
    static final String RUN = "linked-test-" + UUID.randomUUID();
    static final String EMAIL = "one.student@example.com", PASSWORD = "StudentPass123!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }
    record Teacher(long academyId, String slug, String name, long tenantId, String token) {}
    Teacher teacher(String admin, String slug, String name) throws Exception {
        var a = call(post("/api/academies"), admin, Map.of("name", name, "slug", slug, "username", slug.replace('-', '.'), "password", "TeachPass123!"), 200);
        String token = login(slug.replace('-', '.'), "TeachPass123!");
        return new Teacher(a.path("id").asLong(), slug, name, a.path("tenantId").asLong(), token);
    }
    long course(Teacher t, String title, int price) throws Exception {
        return call(post("/api/courses"), t.token(), Map.of("title", title, "price", price), 200).path("summary").path("id").asLong();
    }
    List<String> courseTitles(String token) throws Exception {
        return call(get("/api/courses"), token, null, 200).findValuesAsText("title");
    }

    @Test void oneAccountManyTeachersOneQr() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        long peopleBefore = call(get("/api/public/home"), null, null, 200).path("stats").path("students").asLong();
        Teacher arabic = teacher(admin, "lk-arabic", "مدرسة العربي"), physics = teacher(admin, "lk-physics", "مستر الفيزياء"),
                chemistry = teacher(admin, "lk-chem", "مدرسة الكيمياء"), english = teacher(admin, "lk-english", "مدرسة الإنجليزي"),
                stranger = teacher(admin, "lk-math", "مستر الرياضيات");
        long arabicFree = course(arabic, "النحو من الصفر", 0), physicsFree = course(physics, "الميكانيكا", 0);
        long chemistryPaid = course(chemistry, "الكيمياء العامة", 300);
        course(english, "Grammar", 0);

        // Registers once, with the Arabic teacher.
        String inArabic = call(post("/api/public/register"), null, Map.of("fullName", "طالب واحد", "email", EMAIL, "password", PASSWORD,
                "phone", "01000000000", "tenantSlug", arabic.slug(), "courseId", arabicFree), 200).path("accessToken").asText();
        assertThat(call(get("/api/me/teachers"), inArabic, null, 200)).hasSize(1);

        // Joins the physics teacher from their page, with the same account: lands there, free course open.
        String inPhysics = call(post("/api/me/teachers/join"), inArabic, Map.of("slug", physics.slug(), "courseId", physicsFree), 200).path("accessToken").asText();
        assertThat(call(get("/api/auth/me"), inPhysics, null, 200).path("tenantId").asLong()).isEqualTo(physics.tenantId());
        assertThat(courseTitles(inPhysics)).containsExactly("الميكانيكا");
        assertThat(courseTitles(inArabic)).containsExactly("النحو من الصفر");
        var mine = call(get("/api/me/teachers"), inPhysics, null, 200);
        assertThat(mine.findValuesAsText("slug")).containsExactlyInAnyOrder(arabic.slug(), physics.slug());
        long arabicSeat = 0, physicsSeat = 0;
        for (var m : mine) { if (m.path("slug").asText().equals(arabic.slug())) arabicSeat = m.path("userId").asLong(); else physicsSeat = m.path("userId").asLong(); }
        // Joining again is just switching — no second seat.
        call(post("/api/me/teachers/join"), inArabic, Map.of("slug", physics.slug()), 200);
        assertThat(call(get("/api/me/teachers"), inArabic, null, 200)).hasSize(2);

        // Switching only ever reaches the student's own seats.
        String backInArabic = call(post("/api/me/teachers/switch"), inPhysics, Map.of("userId", arabicSeat), 200).path("accessToken").asText();
        assertThat(call(get("/api/auth/me"), backInArabic, null, 200).path("tenantId").asLong()).isEqualTo(arabic.tenantId());
        String other = call(post("/api/public/register"), null, Map.of("fullName", "طالب تاني", "email", "other.student@example.com", "password", PASSWORD,
                "phone", "01000000001", "tenantSlug", arabic.slug()), 200).path("accessToken").asText();
        long otherId = call(get("/api/auth/me"), other, null, 200).path("id").asLong();
        call(post("/api/me/teachers/switch"), inArabic, Map.of("userId", otherId), 403);

        // Pays the chemistry teacher, gets their code, types it while inside the Arabic teacher's space:
        // joined to chemistry and the paid course is open there.
        String code = call(post("/api/courses/" + chemistryPaid + "/access-codes"), chemistry.token(), Map.of("count", 1), 200).get(0).path("code").asText();
        long chemistrySeat = call(post("/api/courses/redeem-code"), inArabic, Map.of("code", code), 200).path("switchTo").asLong();
        String inChemistry = call(post("/api/me/teachers/switch"), inArabic, Map.of("userId", chemistrySeat), 200).path("accessToken").asText();
        assertThat(courseTitles(inChemistry)).containsExactly("الكيمياء العامة");

        // Signing up again on the English teacher's page with the same email: wrong password is refused,
        // the right one links instead of making a duplicate — and signing in by email still works.
        call(post("/api/public/register"), null, Map.of("fullName", "طالب واحد", "email", EMAIL, "password", "WrongPass999!",
                "phone", "01000000000", "tenantSlug", english.slug()), 409);
        call(post("/api/public/register"), null, Map.of("fullName", "طالب واحد", "email", EMAIL, "password", PASSWORD,
                "phone", "01000000000", "tenantSlug", english.slug()), 200);
        String again = login(EMAIL, PASSWORD);
        assertThat(call(get("/api/me/teachers"), again, null, 200)).hasSize(4);
        assertThat(call(get("/api/public/home"), null, null, 200).path("stats").path("students").asLong())
                .as("a student with four teachers is counted once (plus the other student)").isEqualTo(peopleBefore + 2);

        // Each teacher sees the student's real email, and cannot reset the password of an account they don't own.
        var physicsStudents = call(get("/api/students"), physics.token(), null, 200).path("content");
        assertThat(physicsStudents.findValuesAsText("email")).contains(EMAIL);
        long physicsStudentId = physicsStudents.get(0).path("id").asLong();
        call(put("/api/academies/" + physics.academyId() + "/students/" + physicsStudentId), physics.token(),
                Map.of("courseIds", List.of(physicsFree), "password", "NewTeacherPass1!"), 400);

        // One QR: the pass on the phone is the same with every teacher, and each scan records with the scanner.
        String pass = call(get("/api/gate/my-pass"), inPhysics, null, 200).path("token").asText();
        assertThat(call(get("/api/gate/my-pass"), inArabic, null, 200).path("token").asText()).isEqualTo(pass);
        var scan = call(post("/api/gate/scan/" + pass), physics.token(), null, 200);
        assertThat(scan.path("teacher").path("name").asText()).isEqualTo("مستر الفيزياء");
        assertThat(scan.path("message").asText()).contains("مستر الفيزياء").contains("الحضور");
        assertThat(call(get("/api/gate/log"), physics.token(), null, 200)).hasSize(1);
        assertThat(call(get("/api/gate/log"), arabic.token(), null, 200)).isEmpty();
        // A teacher the student never joined: refused, nothing recorded, nothing about the student revealed.
        var refused = call(post("/api/gate/scan/" + pass), stranger.token(), null, 403);
        assertThat(refused.toString()).doesNotContain("طالب واحد").doesNotContain("العربي");
        assertThat(call(get("/api/gate/log"), stranger.token(), null, 200)).isEmpty();
        // Per-space codes never travel: the student code from the physics space means nothing to the stranger.
        String studentCode = physicsStudents.get(0).path("code").asText();
        call(post("/api/gate/scan"), stranger.token(), Map.of("code", studentCode), 404);
        // Head office at the door sees every teacher: it must choose, and only then is anything recorded.
        var ask = call(post("/api/gate/scan/" + pass), admin, null, 200);
        assertThat(ask.path("choices").size()).isGreaterThan(1);
        assertThat(ask.hasNonNull("studentId")).as("nothing recorded yet").isFalse();
        var chosen = call(post("/api/gate/scan/" + pass + "?academyId=" + chemistry.academyId()), admin, null, 200);
        assertThat(chosen.path("teacher").path("name").asText()).isEqualTo("مدرسة الكيمياء");
        assertThat(call(get("/api/gate/log"), chemistry.token(), null, 200)).hasSize(1);
        // The student's own door history covers all their teachers, each entry marked with its teacher.
        long ownArabicId = call(get("/api/students/me"), inArabic, null, 200).path("summary").path("id").asLong();
        var history = call(get("/api/gate/students/" + ownArabicId + "/log"), inArabic, null, 200);
        assertThat(history).hasSize(2);
        assertThat(String.join(" | ", history.findValuesAsText("teacher"))).contains("مستر الفيزياء").contains("مدرسة الكيمياء");

        // Class attendance by the session QR: looking at the Arabic teacher, the student scans the physics class
        // QR — it is recorded with the physics teacher, on the student's seat there, and nowhere else.
        long sessionId = call(post("/api/attendance/sessions"), physics.token(), Map.of("courseId", physicsFree, "title", "حصة الميكانيكا"), 200).path("id").asLong();
        String classQr = call(post("/api/attendance/sessions/" + sessionId + "/qr"), physics.token(), null, 200).path("token").asText();
        assertThat(call(post("/api/attendance/check-in"), inArabic, Map.of("token", classQr), 200).path("studentName").asText()).isEqualTo("طالب واحد");
        assertThat(call(get("/api/attendance/sessions/" + sessionId + "/roster"), physics.token(), null, 200).findValuesAsText("status")).contains("PRESENT");
        // A class of a teacher the student never joined is refused.
        long mathCourse = course(stranger, "الجبر", 0);
        long mathSession = call(post("/api/attendance/sessions"), stranger.token(), Map.of("courseId", mathCourse, "title", "حصة الجبر"), 200).path("id").asLong();
        String mathQr = call(post("/api/attendance/sessions/" + mathSession + "/qr"), stranger.token(), null, 200).path("token").asText();
        call(post("/api/attendance/check-in"), inArabic, Map.of("token", mathQr), 400);

        // The Arabic teacher removes the student: the login keeps working and lands with a teacher they still have.
        long arabicStudentId = 0;
        for (var s : call(get("/api/students"), arabic.token(), null, 200).path("content"))
            if (EMAIL.equals(s.path("email").asText())) arabicStudentId = s.path("id").asLong();
        call(delete("/api/academies/" + arabic.academyId() + "/students/" + arabicStudentId), arabic.token(), null, 200);
        call(get("/api/auth/me"), backInArabic, null, 401);
        call(get("/api/auth/me"), inPhysics, null, 200);
        String afterRemoval = login(EMAIL, PASSWORD);
        assertThat(call(get("/api/auth/me"), afterRemoval, null, 200).path("tenantId").asLong()).isNotEqualTo(arabic.tenantId());
        assertThat(call(get("/api/me/teachers"), afterRemoval, null, 200).findValuesAsText("slug")).doesNotContain(arabic.slug()).hasSize(3);

        // Changing the password — even from inside another teacher's space — changes the one account and ends
        // the sessions with every teacher.
        call(put("/api/users/me/password"), inChemistry, Map.of("currentPassword", PASSWORD, "newPassword", "BrandNewPass123!"), 200);
        call(get("/api/auth/me"), inPhysics, null, 401);
        call(get("/api/auth/me"), afterRemoval, null, 401);
        String fresh = login(EMAIL, "BrandNewPass123!");
        assertThat(call(get("/api/me/teachers"), fresh, null, 200)).hasSize(3);
        assertThat(physicsSeat).isPositive();
    }
}
