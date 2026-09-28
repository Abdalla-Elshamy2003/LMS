package com.manarah.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Blocking with a reason: head office blocks a teacher or a student everywhere, a teacher blocks a student in their space. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class AccountBlockTest {
    static final String RUN = "account-block-" + UUID.randomUUID();
    static final String PASS = "TestPass123!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    ResultActions call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }
    JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    JsonNode ok(ResultActions r) throws Exception { return read(r.andExpect(status().isOk())); }
    JsonNode login(String username, String password) throws Exception {
        return read(call(post("/api/auth/login"), null, Map.of("email", username, "password", password)));
    }
    String token(String username) throws Exception {
        return ok(call(post("/api/auth/login"), null, Map.of("email", username, "password", PASS))).path("accessToken").asText();
    }
    /** Refused because of a block: 403, the block's code, and the reason in readable Arabic. */
    void blocked(ResultActions r, String reason) throws Exception {
        var body = read(r.andExpect(status().isForbidden()));
        assertThat(body.path("details").path("code").asText()).isEqualTo("ACCOUNT_BLOCKED");
        assertThat(body.path("message").asText()).contains(reason);
    }
    long teacher(String admin, String slug, String username) throws Exception {
        return ok(call(post("/api/admin/teachers"), admin, Map.of("name", "مستر " + slug, "subject", "الرياضيات", "slug", slug,
                "username", username, "password", PASS, "published", true, "courses", List.of(Map.of("title", "كورس " + slug))))).path("academyId").asLong();
    }

    @Test void blocksAndUnblocksWithTheirReasons() throws Exception {
        String admin = ok(call(post("/api/auth/login"), null, Map.of("email", "admin@manarah.io", "password", "manarah123"))).path("accessToken").asText();
        long a = teacher(admin, "block-a", "block.a"), b = teacher(admin, "block-b", "block.b");
        String teacherA = token("block.a"), teacherB = token("block.b");
        long s1 = ok(call(post("/api/academies/" + a + "/students"), teacherA, Map.of("fullName", "طالب أول", "username", "block.s1", "password", PASS, "courseIds", List.of()))).path("id").asLong();
        long s2 = ok(call(post("/api/academies/" + a + "/students"), teacherA, Map.of("fullName", "طالب تاني", "username", "block.s2", "password", PASS, "courseIds", List.of()))).path("id").asLong();
        var s1Login = ok(call(post("/api/auth/login"), null, Map.of("email", "block.s1", "password", PASS)));
        long s1User = s1Login.path("user").path("id").asLong();
        ok(call(post("/api/me/teachers/join"), s1Login.path("accessToken").asText(), Map.of("slug", "block-b"))); // s1 studies with a and b
        ok(call(post("/api/academies/" + a + "/assistants"), teacherA, Map.of("fullName", "مساعد", "email", "block.assistant@example.com", "password", PASS)));
        String s2Token = token("block.s2");

        // Head office blocks teacher a: the right password hears why, a wrong one hears nothing; live sessions stop too.
        call(put("/api/admin/teachers/" + a + "/block"), admin, Map.of("reason", " ")).andExpect(status().isBadRequest());
        ok(call(put("/api/admin/teachers/" + a + "/block"), admin, Map.of("reason", "مخالفة شروط المنصة")));
        blocked(call(post("/api/auth/login"), null, Map.of("email", "block.a", "password", PASS)), "مخالفة شروط المنصة");
        var wrong = login("block.a", "WrongPass123!");
        assertThat(wrong.path("status").asInt()).isEqualTo(401);
        assertThat(wrong.toString()).doesNotContain("مخالفة");
        blocked(call(get("/api/auth/me"), teacherA, null), "مخالفة شروط المنصة");
        blocked(call(post("/api/auth/login"), null, Map.of("email", "block.assistant@example.com", "password", PASS)), "مخالفة شروط المنصة");
        assertThat(ok(call(get("/api/admin/blocks"), admin, null)).path("blocked").findValuesAsText("kind")).contains("TEACHER");
        ok(call(delete("/api/admin/teachers/" + a + "/block"), admin, null));
        teacherA = token("block.a");

        // Teacher a blocks s1 in their space only: s1 lands with teacher b, and hears a's reason trying to go back.
        ok(call(put("/api/academies/" + a + "/students/" + s1 + "/block"), teacherA, Map.of("reason", "غياب متكرر")));
        var landed = ok(call(post("/api/auth/login"), null, Map.of("email", "block.s1", "password", PASS)));
        String s1Token = landed.path("accessToken").asText();
        assertThat(ok(call(get("/api/academy-context"), s1Token, null)).path("slug").asText()).isEqualTo("block-b");
        assertThat(ok(call(get("/api/me/teachers"), s1Token, null)).findValuesAsText("slug")).containsExactly("block-b");
        assertThat(read(call(post("/api/me/teachers/switch"), s1Token, Map.of("userId", s1User)).andExpect(status().isForbidden()))
                .path("message").asText()).contains("غياب متكرر");

        // Blocked by their only teacher, s2 cannot get in at all, and a session already open stops.
        ok(call(put("/api/academies/" + a + "/students/" + s2 + "/block"), teacherA, Map.of("reason", "لم يسدد الاشتراك")));
        blocked(call(post("/api/auth/login"), null, Map.of("email", "block.s2", "password", PASS)), "لم يسدد الاشتراك");
        blocked(call(get("/api/auth/me"), s2Token, null), "لم يسدد الاشتراك");
        // Another teacher can't touch a's students.
        call(put("/api/academies/" + a + "/students/" + s2 + "/block"), teacherB, Map.of("reason", "x")).andExpect(status().isForbidden());
        call(delete("/api/academies/" + a + "/students/" + s2 + "/block"), teacherB, null).andExpect(status().isForbidden());
        // Head office can lift a teacher's block.
        ok(call(delete("/api/admin/seats/" + s2 + "/block"), admin, null));
        token("block.s2");

        // Head office blocks s1 everywhere; teacher a sees it but lifting their own block doesn't let s1 in.
        ok(call(put("/api/admin/students/" + s1User + "/block"), admin, Map.of("reason", "حساب مشترك مع طالب تاني")));
        blocked(call(post("/api/auth/login"), null, Map.of("email", "block.s1", "password", PASS)), "حساب مشترك");
        var seats = ok(call(get("/api/academies/" + a + "/blocks"), teacherA, null));
        assertThat(seats.findValuesAsText("platformReason")).contains("حساب مشترك مع طالب تاني");
        ok(call(delete("/api/academies/" + a + "/students/" + s1 + "/block"), teacherA, null));
        blocked(call(post("/api/auth/login"), null, Map.of("email", "block.s1", "password", PASS)), "حساب مشترك");
        ok(call(delete("/api/admin/students/" + s1User + "/block"), admin, null));
        token("block.s1");
    }

    @Test void aTeacherEditsHidesAndDeletesTheirOwnCourses() throws Exception {
        String admin = ok(call(post("/api/auth/login"), null, Map.of("email", "admin@manarah.io", "password", "manarah123"))).path("accessToken").asText();
        long a = teacher(admin, "course-own", "course.own");
        teacher(admin, "course-other", "course.other");
        String mine = token("course.own"), other = token("course.other");
        long course = ok(call(get("/api/admin/teachers/" + a), admin, null)).path("courses").path(0).path("id").asLong();

        var edited = ok(call(put("/api/courses/" + course), mine, Map.of("title", "الجبر والهندسة", "price", 250, "status", "HIDDEN")));
        assertThat(edited.path("title").asText()).isEqualTo("الجبر والهندسة");
        assertThat(edited.path("status").asText()).isEqualTo("HIDDEN");
        call(put("/api/courses/" + course), mine, Map.of("status", "DELETED")).andExpect(status().isBadRequest());
        call(put("/api/courses/" + course), other, Map.of("title", "x")).andExpect(status().isNotFound());
        call(delete("/api/courses/" + course), other, null).andExpect(status().isNotFound());
        ok(call(delete("/api/courses/" + course), mine, null));
        call(get("/api/courses"), mine, null).andExpect(jsonPath("$.length()").value(0));
    }
}
