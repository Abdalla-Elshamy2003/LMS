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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A student picks a course on a teacher's page and signs up: the course's year becomes theirs, a paid course waits on
 * their dashboard until the teacher is paid (a code or the teacher's "فعّل"), a free one opens at once, and whatever
 * the teacher publishes later for their year shows up for them — each course still subscribed to (and paid) on its own.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class StudentCourseSignupTest {
    static final String RUN = "signup-test-" + UUID.randomUUID();
    static final String PASSWORD = "StudentPass123!";
    static final String SECOND = "الصف الثاني الثانوي", FIRST = "الصف الأول الثانوي";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        // A distinct client address per call keeps the per-IP signup limit out of the way.
        req.header("X-Forwarded-For", "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }
    long course(String token, String title, int price, String year) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", title, "price", price, "subject", "الفيزياء"));
        if (year != null) body.put("grade", year);
        return call(post("/api/courses"), token, body, 200).path("summary").path("id").asLong();
    }
    Map<Long, JsonNode> catalog(String token) throws Exception {
        Map<Long, JsonNode> byId = new LinkedHashMap<>();
        call(get("/api/me/catalog"), token, null, 200).path("courses").forEach(c -> byId.put(c.path("id").asLong(), c));
        return byId;
    }
    Map<String, Object> signup(String email, Long courseId, String grade) {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "طالب " + email.substring(0, 4), "email", email,
                "password", PASSWORD, "phone", "01011112222", "tenantSlug", "sc-physics"));
        if (courseId != null) body.put("courseId", courseId);
        if (grade != null) body.put("grade", grade);
        return body;
    }

    @Test void pickACourseSignUpPayAndSeeYourYear() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        long academyId = call(post("/api/academies"), admin, Map.of("name", "مستر الفيزياء", "slug", "sc-physics",
                "username", "sc.physics", "password", "TeachPass123!"), 200).path("id").asLong();
        String teacher = login("sc.physics", "TeachPass123!");
        long motion = course(teacher, "الحركة", 200, SECOND);
        long review = course(teacher, "مراجعة مجانية", 0, "تانية ثانوي");
        long forAll = course(teacher, "أساسيات لكل السنين", 0, null);
        long firstYear = course(teacher, "القياس", 150, FIRST);
        long hidden = course(teacher, "كورس متشال", 100, SECOND);
        call(put("/api/admin/courses/" + hidden), admin, Map.of("price", 100, "status", "HIDDEN"), 200);

        // Signing up needs a teacher, and only a course the teacher still offers.
        call(post("/api/public/register"), null, Map.of("fullName", "بدون مدرس", "email", "no.teacher@example.com",
                "password", PASSWORD, "phone", "01011112222"), 400);
        call(post("/api/public/register"), null, signup("hidden.pick@example.com", hidden, null), 400);

        // From the paid "الحركة" card, with no year chosen: the course's year becomes the student's year.
        String student = call(post("/api/public/register"), null, signup("paid.pick@example.com", motion, null), 200)
                .path("accessToken").asText();
        JsonNode mine = call(get("/api/me/catalog"), student, null, 200);
        assertThat(mine.path("grade").asText()).isEqualTo(SECOND);
        assertThat(mine.path("teacher").path("slug").asText()).isEqualTo("sc-physics");
        Map<Long, JsonNode> seen = catalog(student);
        assertThat(seen.get(motion).path("state").asText()).isEqualTo("PENDING");
        assertThat(seen.get(review).path("state").asText()).isEqualTo("NONE");      // same year, spelled differently
        assertThat(seen.get(review).path("forYear").asBoolean()).isTrue();
        assertThat(seen.get(forAll).path("state").asText()).isEqualTo("NONE");      // no year: for everyone
        assertThat(seen).doesNotContainKeys(firstYear, hidden);
        // Waiting for payment opens nothing.
        call(get("/api/courses/" + motion), student, null, 403);
        assertThat(call(get("/api/learning"), student, null, 200).size()).isZero();

        // The teacher sees the request with the student's contact details; unpaid requests aren't counted as students.
        JsonNode pending = call(get("/api/enrollments/pending"), teacher, null, 200);
        assertThat(pending.size()).isEqualTo(1);
        assertThat(pending.get(0).path("email").asText()).isEqualTo("paid.pick@example.com");
        assertThat(pending.get(0).path("courseTitle").asText()).isEqualTo("الحركة");
        assertThat(call(get("/api/dashboard/teacher"), teacher, null, 200).path("students").asLong()).isZero();
        long studentId = pending.get(0).path("studentId").asLong();

        // A free course opens straight from the dashboard; asking twice changes nothing.
        assertThat(call(post("/api/me/catalog/" + review + "/request"), student, null, 200).path("state").asText()).isEqualTo("ACTIVE");
        assertThat(call(post("/api/me/catalog/" + review + "/request"), student, null, 200).path("state").asText()).isEqualTo("ACTIVE");
        call(get("/api/courses/" + review), student, null, 200);
        assertThat(call(post("/api/me/catalog/" + motion + "/request"), student, null, 200).path("state").asText()).isEqualTo("PENDING");

        // The teacher got paid: activate. Only once, and only their own requests.
        long requestId = pending.get(0).path("enrollmentId").asLong();
        call(post("/api/enrollments/" + requestId + "/activate"), student, null, 403);
        call(post("/api/enrollments/" + requestId + "/activate"), teacher, null, 200);
        call(post("/api/enrollments/" + requestId + "/activate"), teacher, null, 409);
        call(get("/api/courses/" + motion), student, null, 200);
        assertThat(catalog(student).get(motion).path("state").asText()).isEqualTo("ACTIVE");
        assertThat(call(get("/api/dashboard/teacher"), teacher, null, 200).path("students").asLong()).isEqualTo(2);

        // A course the teacher publishes later for this year appears by itself, marked new; another year's doesn't.
        long later = course(teacher, "الكهربية", 250, "2 ثانوي");
        long laterFirst = course(teacher, "الموجات", 250, FIRST);
        seen = catalog(student);
        assertThat(seen.get(later).path("state").asText()).isEqualTo("NONE");
        assertThat(seen.get(later).path("isNew").asBoolean()).isTrue();
        assertThat(seen).doesNotContainKey(laterFirst);

        // Asked for, then turned down by the teacher: back to not subscribed.
        call(post("/api/me/catalog/" + later + "/request"), student, null, 200);
        long laterRequest = call(get("/api/enrollments/pending"), teacher, null, 200).get(0).path("enrollmentId").asLong();
        call(delete("/api/enrollments/" + laterRequest + "/request"), teacher, null, 200);
        assertThat(catalog(student).get(later).path("state").asText()).isEqualTo("NONE");

        // A new school year: the catalog follows it, and what they already study stays.
        call(put("/api/me/catalog/grade"), student, Map.of("grade", "أولى ثانوي"), 200);
        seen = catalog(student);
        assertThat(seen).containsKeys(firstYear, laterFirst, motion, review, forAll).doesNotContainKey(later);
        assertThat(seen.get(motion).path("forYear").asBoolean()).isFalse();

        assertThat(call(post("/api/me/catalog/" + forAll + "/request"), student, null, 200).path("state").asText()).isEqualTo("ACTIVE");

        // Clicking a course on the page of a teacher they already have asks for it too.
        call(post("/api/me/teachers/join"), student, Map.of("slug", "sc-physics", "courseId", firstYear), 200);
        assertThat(catalog(student).get(firstYear).path("state").asText()).isEqualTo("PENDING");

        // The teacher's own student editor: unticking closes open courses but leaves a payment request waiting,
        // and a closed course can't be reopened by asking again.
        call(put("/api/academies/" + academyId + "/students/" + studentId), admin, Map.of("courseIds", List.of()), 200);
        seen = catalog(student);
        assertThat(seen.get(firstYear).path("state").asText()).isEqualTo("PENDING");
        assertThat(seen.get(forAll).path("state").asText()).isEqualTo("CLOSED");
        call(post("/api/me/catalog/" + forAll + "/request"), student, null, 403);

        // From a free card: open at once, with the course's year.
        String second = call(post("/api/public/register"), null, signup("free.pick@example.com", review, null), 200)
                .path("accessToken").asText();
        assertThat(call(get("/api/me/catalog"), second, null, 200).path("grade").asText()).isEqualTo("تانية ثانوي");
        assertThat(catalog(second).get(review).path("state").asText()).isEqualTo("ACTIVE");
        // Already with this teacher: sign in instead.
        call(post("/api/public/register"), null, signup("free.pick@example.com", motion, null), 409);
    }
}
