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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Head office's teacher page: one-shot create with courses, full course edits, and what "delete" means to everyone. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class AdminTeacherControlTest {
    static final String RUN = "admin-teacher-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }
    JsonNode ok(ResultActions r) throws Exception { return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    String login(String username, String password) throws Exception {
        return ok(call(post("/api/auth/login"), null, Map.of("email", username, "password", password))).path("accessToken").asText();
    }
    Map<String, Object> teacher(String slug, String username, List<Map<String, Object>> courses) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "مستر الكيمياء"); m.put("subject", "الكيمياء"); m.put("slug", slug);
        m.put("username", username); m.put("password", "TestPass123!"); m.put("published", true); m.put("courses", courses);
        return m;
    }

    @Test void createEditHideAndDeleteTeachersAndCourses() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");

        // A course without a title refuses the whole teacher: nothing is left half-made.
        call(post("/api/admin/teachers"), admin, teacher("studio-broken", "studio.broken", List.of(Map.of("price", 100))))
                .andExpect(status().isBadRequest());
        call(post("/api/auth/login"), null, Map.of("email", "studio.broken", "password", "TestPass123!")).andExpect(status().is4xxClientError());
        call(get("/api/public/academies/studio-broken"), null, null).andExpect(status().isNotFound());

        // Teacher, sign-in, page and courses in one call; blank page texts get lines built from the name and subject.
        var made = ok(call(post("/api/admin/teachers"), admin, teacher("studio-chem", "studio.chem", List.of(
                Map.of("title", "كيمياء الباب الأول", "grade", "الصف الثالث الثانوي", "price", 200, "discountPercent", 10, "status", "ACTIVE"),
                Map.of("title", "مراجعة مخفية", "price", 0, "status", "HIDDEN")))));
        long academy = made.path("academyId").asLong();
        assertThat(made.path("tagline").asText()).isEqualTo("مدرس الكيمياء");
        assertThat(made.path("courses")).hasSize(2);
        long course = made.path("courses").path(0).path("id").asLong();
        assertThat(made.path("courses").path(0).path("finalPrice").decimalValue()).isEqualByComparingTo("180");
        call(get("/api/public/academies/studio-chem"), null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1)).andExpect(jsonPath("$.courses[0].finalPrice").value(180));
        String teacherToken = login("studio.chem", "TestPass123!");

        // The teacher's own payment numbers survive head office editing the page.
        var page = new LinkedHashMap<String, Object>(Map.of("name", "مستر الكيمياء", "tagline", "مدرس الكيمياء", "headline", "عنوان",
                "description", "وصف", "aboutText", "نبذة", "subject", "الكيمياء", "phone", "", "demoContent", false, "published", true));
        page.put("instapayNumber", "01000000000"); page.put("vodafoneCashNumber", ""); page.put("paymentNote", "");
        ok(call(put("/api/academies/" + academy), teacherToken, page));
        var edited = new LinkedHashMap<String, Object>(Map.of("name", "مستر الكيمياء", "subject", "الكيمياء", "headline", "الكيمياء فهم مش حفظ",
                "username", "studio.chem", "password", ""));
        assertThat(ok(call(put("/api/admin/teachers/" + academy), admin, edited)).path("headline").asText()).isEqualTo("الكيمياء فهم مش حفظ");
        call(get("/api/public/academies/studio-chem"), null, null).andExpect(jsonPath("$.profile.instapayNumber").value("01000000000"));
        login("studio.chem", "TestPass123!"); // a blank password kept the old one

        // Any course detail can change; what isn't sent stays.
        var course1 = ok(call(put("/api/admin/courses/" + course), admin, Map.of("title", "كيمياء عضوية", "description", "شرح كامل")));
        assertThat(course1.path("title").asText()).isEqualTo("كيمياء عضوية");
        assertThat(course1.path("price").decimalValue()).isEqualByComparingTo("200");
        call(put("/api/admin/courses/" + course), admin, Map.of("status", "DELETED")).andExpect(status().isBadRequest());

        // A student with the course loses it when head office deletes it.
        ok(call(post("/api/academies/" + academy + "/students"), teacherToken,
                Map.of("fullName", "طالب الكيمياء", "username", "studio.student", "password", "StudentPass123!", "courseIds", List.of(course))));
        String student = login("studio.student", "StudentPass123!");
        call(get("/api/learning"), student, null).andExpect(jsonPath("$.length()").value(1));
        call(delete("/api/admin/courses/" + course), admin, null).andExpect(status().isOk());
        call(get("/api/learning"), student, null).andExpect(jsonPath("$.length()").value(0));
        call(get("/api/courses/" + course), student, null).andExpect(status().isNotFound());
        call(get("/api/courses"), teacherToken, null).andExpect(jsonPath("$.length()").value(1));
        call(get("/api/public/academies/studio-chem"), null, null).andExpect(jsonPath("$.courses.length()").value(0));
        assertThat(ok(call(get("/api/admin/courses"), admin, null)).findValuesAsText("id")).doesNotContain(String.valueOf(course));

        // A course added later shows on the teacher's page.
        ok(call(post("/api/admin/teachers/" + academy + "/courses"), admin, Map.of("title", "كيمياء الباب الثاني", "price", 150)));
        call(get("/api/public/academies/studio-chem"), null, null).andExpect(jsonPath("$.courses.length()").value(1));

        // Deleting the teacher: gone from the site and the admin, sign-in closed, link and username free again.
        call(delete("/api/admin/teachers/" + academy), admin, null).andExpect(status().isOk());
        call(post("/api/auth/login"), null, Map.of("email", "studio.chem", "password", "TestPass123!")).andExpect(status().is4xxClientError());
        call(get("/api/public/academies/studio-chem"), null, null).andExpect(status().isNotFound());
        call(get("/api/admin/teachers/" + academy), admin, null).andExpect(status().isNotFound());
        assertThat(ok(call(get("/api/admin/teachers"), admin, null)).findValuesAsText("academyId")).doesNotContain(String.valueOf(academy));
        assertThat(ok(call(get("/api/public/home"), null, null)).findValuesAsText("slug")).doesNotContain("studio-chem");
        call(get("/api/learning"), student, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        ok(call(post("/api/admin/teachers"), admin, teacher("studio-chem", "studio.chem", List.of())));
    }
}
