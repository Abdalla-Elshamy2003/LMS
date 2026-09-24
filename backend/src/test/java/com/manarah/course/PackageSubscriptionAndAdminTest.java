package com.manarah.course;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The first package filled with demo content, bought at one price (by code, as a visitor or a signed-in student),
 * cancelled without touching a course bought separately; head office's control center and its guards; and the
 * create-only admin bootstrap.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class PackageSubscriptionAndAdminTest {
    static final String RUN = "package-test-" + UUID.randomUUID();
    static final String PASSWORD = "StudentPass123!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired com.manarah.admin.AdminBootstrap bootstrap;

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }
    List<String> courseTitles(String token) throws Exception { return call(get("/api/courses"), token, null, 200).findValuesAsText("title"); }

    @Test void packageDemoContentSubscriptionsAndControlCenter() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");

        // Demo content: five teachers, six courses each, linked into the package — and pressing it again adds nothing.
        var seeded = call(post("/api/admin/demo/package-content"), admin, null, 200);
        assertThat(seeded.path("teachersCreated").asInt()).isEqualTo(5);
        assertThat(seeded.path("coursesCreated").asInt()).isEqualTo(30);
        assertThat(seeded.path("membersLinked").asInt()).isEqualTo(5);
        var again = call(post("/api/admin/demo/package-content"), admin, null, 200);
        assertThat(again.path("teachersCreated").asInt() + again.path("coursesCreated").asInt() + again.path("membersLinked").asInt()).isZero();
        var landing = call(get("/api/public/bundles/excellence"), null, null, 200);
        assertThat(landing.path("price").asInt()).isEqualTo(2999);
        assertThat(landing.path("coursesValue").asDouble()).isGreaterThan(2999);
        for (var m : landing.path("members")) {
            assertThat(m.path("teacher").path("courses")).hasSize(6);
            assertThat(m.path("teacher").path("videos")).hasSize(6);
        }
        assertThat(landing.path("members").get(2).path("teacher").path("courses").get(0).path("year").asText()).isEqualTo("الصف الأول الثانوي");

        // Head office sets where to pay and mints package codes.
        long bundleId = 0;
        for (var b : call(get("/api/bundles"), admin, null, 200)) if (b.path("slug").asText().equals("excellence")) bundleId = b.path("id").asLong();
        var current = (com.fasterxml.jackson.databind.node.ObjectNode) call(get("/api/bundles"), admin, null, 200).get(0);
        current.put("instapayNumber", "01000000000"); current.put("price", 2500);
        call(put("/api/bundles/" + bundleId), admin, current, 200);
        assertThat(call(get("/api/public/bundles/excellence"), null, null, 200).path("payment").path("instapayNumber").asText()).isEqualTo("01000000000");
        var codes = call(post("/api/bundles/" + bundleId + "/codes"), admin, Map.of("count", 3), 200);
        String visitorCode = codes.get(0).path("code").asText(), studentCode = codes.get(1).path("code").asText(), spare = codes.get(2).path("code").asText();
        assertThat(visitorCode).startsWith("PKG-");

        // A visitor signs up with a package code: one account, all five teachers, thirty courses, one "باقتي".
        String visitor = call(post("/api/public/redeem-code"), null, Map.of("code", visitorCode, "fullName", "زائر الباقة",
                "email", "visitor.package@example.com", "password", PASSWORD, "phone", "01011111111"), 200).path("accessToken").asText();
        var mine = call(get("/api/me/packages"), visitor, null, 200);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("teachers")).hasSize(5);
        int total = 0; for (var t : mine.get(0).path("teachers")) total += t.path("courses").size();
        assertThat(total).isEqualTo(30);
        assertThat(call(get("/api/me/teachers"), visitor, null, 200)).hasSize(5);
        call(post("/api/public/redeem-code"), null, Map.of("code", visitorCode, "fullName", "تاني", "email", "second@example.com",
                "password", PASSWORD, "phone", "01011111112"), 409);

        // A student who already studies with the physics teacher, and bought one of his courses with that teacher's own
        // code, types the package code in the ordinary code box: same account, no second physics seat.
        long physicsAcademy = 0;
        for (var t : call(get("/api/admin/teachers"), admin, null, 200)) if (t.path("slug").asText().equals("excellence-physics")) physicsAcademy = t.path("academyId").asLong();
        var physicsCourses = call(get("/api/public/academies/excellence-physics"), null, null, 200).path("courses");
        long boughtCourse = physicsCourses.get(1).path("id").asLong();
        String student = call(post("/api/public/register"), null, Map.of("fullName", "طالب الفيزياء", "email", "physics.first@example.com",
                "password", PASSWORD, "phone", "01022222222", "tenantSlug", "excellence-physics"), 200).path("accessToken").asText();
        String courseCode = mvc.perform(post("/api/courses/" + boughtCourse + "/access-codes").header("Authorization", "Bearer " + admin)
                .header("X-Academy-Id", physicsAcademy).contentType(MediaType.APPLICATION_JSON).content("{\"count\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        call(post("/api/courses/redeem-code"), student, Map.of("code", json.readTree(courseCode).get(0).path("code").asText()), 200);
        assertThat(courseTitles(student)).containsExactly(physicsCourses.get(1).path("title").asText());
        assertThat(call(post("/api/courses/redeem-code"), student, Map.of("code", studentCode), 200).path("bundleId").asLong()).isEqualTo(bundleId);
        assertThat(call(get("/api/me/teachers"), student, null, 200)).hasSize(5);
        assertThat(courseTitles(student)).hasSize(6);

        // Cancelling the package closes what it opened — never the course bought on its own.
        var subs = call(get("/api/bundles/" + bundleId + "/subscribers"), admin, null, 200);
        long subId = 0; long studentUserId = 0;
        for (var s : subs) if (s.path("login").asText().equals("physics.first@example.com")) { subId = s.path("id").asLong(); studentUserId = s.path("userId").asLong(); }
        call(delete("/api/bundles/subscriptions/" + subId), admin, null, 200);
        assertThat(courseTitles(student)).containsExactly(physicsCourses.get(1).path("title").asText());
        assertThat(call(get("/api/me/packages"), student, null, 200)).isEmpty();
        // ...and head office can grant it back directly after confirming a transfer.
        call(post("/api/bundles/" + bundleId + "/subscribers"), admin, Map.of("login", "physics.first@example.com"), 200);
        assertThat(courseTitles(student)).hasSize(6);
        call(delete("/api/bundles/codes/" + codes.get(2).path("id").asLong()), admin, null, 200);
        call(post("/api/courses/redeem-code"), student, Map.of("code", spare), 400);

        // The control center: the whole platform, one row per person.
        var overview = call(get("/api/admin/overview"), admin, null, 200).path("kpis");
        assertThat(overview.path("teachers").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(overview.path("packageSubscribers").asInt()).isEqualTo(2);
        var teachers = call(get("/api/admin/teachers"), admin, null, 200);
        assertThat(teachers.toString()).contains("excellence-chemistry").contains("باقة التفوّق");
        var students = call(get("/api/admin/students?q=physics.first"), admin, null, 200);
        assertThat(students).hasSize(1);
        assertThat(students.get(0).path("teachers")).hasSize(5);
        assertThat(students.get(0).path("packages").toString()).contains("باقة التفوّق");
        // suspend / restore the one account, reset its password
        call(put("/api/admin/students/" + studentUserId + "/active"), admin, Map.of("active", false), 200);
        call(post("/api/auth/login"), null, Map.of("email", "physics.first@example.com", "password", PASSWORD), 401);
        call(put("/api/admin/students/" + studentUserId + "/active"), admin, Map.of("active", true), 200);
        call(put("/api/admin/students/" + studentUserId + "/password"), admin, Map.of("password", "ResetByAdmin123!"), 200);
        call(get("/api/auth/me"), student, null, 401);
        login("physics.first@example.com", "ResetByAdmin123!");
        // course price and visibility across teachers
        var courseRows = call(get("/api/admin/courses").param("q", "الكيمياء العضوية"), admin, null, 200);
        long organic = courseRows.get(0).path("id").asLong();
        call(put("/api/admin/courses/" + organic), admin, Map.of("price", 275, "status", "HIDDEN"), 200);
        var chemistry = call(get("/api/public/academies/excellence-chemistry"), null, null, 200).path("courses");
        assertThat(chemistry.findValuesAsText("title")).doesNotContain("الكيمياء العضوية");
        call(put("/api/admin/courses/" + organic), admin, Map.of("status", "SOMETHING"), 400);
        call(put("/api/admin/teachers/" + physicsAcademy + "/published"), admin, Map.of("published", false), 200);
        call(get("/api/public/academies/excellence-physics"), null, null, 404);
        call(put("/api/admin/teachers/" + physicsAcademy + "/published"), admin, Map.of("published", true), 200);

        // Guards: a teacher never reaches the control center or the demo button.
        call(post("/api/academies"), admin, Map.of("name", "مدرس عادي", "slug", "plain-teacher", "username", "plain.teacher", "password", "TeachPass123!"), 200);
        String teacher = login("plain.teacher", "TeachPass123!");
        call(get("/api/admin/overview"), teacher, null, 403);
        call(get("/api/admin/students"), teacher, null, 403);
        call(post("/api/admin/demo/package-content"), teacher, null, 403);
        call(post("/api/bundles/" + bundleId + "/codes"), teacher, Map.of("count", 1), 403);
        call(post("/api/me/packages/redeem"), teacher, Map.of("code", "PKG-XXXX-XXXX"), 403);

        // The owner's admin is created once from a BCrypt hash, and never changed by it afterwards.
        assertThat(bootstrap.createIfMissing("owner.control", encoder.encode("OwnerPass12345!"))).isTrue();
        login("owner.control", "OwnerPass12345!");
        assertThat(bootstrap.createIfMissing("owner.control", encoder.encode("Takeover12345!"))).isFalse();
        login("owner.control", "OwnerPass12345!");
        assertThat(bootstrap.createIfMissing("bad.hash", "not-a-bcrypt-hash")).isFalse();
        call(get("/api/admin/overview"), login("owner.control", "OwnerPass12345!"), null, 200);
    }
}
