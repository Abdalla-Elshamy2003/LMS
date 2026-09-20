package com.manarah.course;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class TeacherAcademyWorkflowTest {
    static final String RUN = "academy-test-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String body(Object value) throws Exception { return json.writeValueAsString(value); }
    JsonNode postJson(String path, String token, Object value) throws Exception {
        return json.readTree(mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body(value)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String login(String username, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", username, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    @Test void independentAcademiesStudentAssignmentRevocationDiscountAndPublication() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        var a = postJson("/api/academies", admin, Map.of("name", "مدرس الرياضيات", "slug", "test-math", "username", "test.math", "password", "TestPass123!"));
        var b = postJson("/api/academies", admin, Map.of("name", "مدرس الفيزياء", "slug", "test-physics", "username", "test.physics", "password", "TestPass123!"));
        long aid = a.path("id").asLong(), bid = b.path("id").asLong();
        String teacher = login("test.math", "TestPass123!"), other = login("test.physics", "TestPass123!");
        long course = postJson("/api/courses", teacher, Map.of("title", "الجبر", "price", 300)).path("summary").path("id").asLong();
        long hidden = postJson("/api/courses", teacher, Map.of("title", "التفاضل", "price", 100)).path("summary").path("id").asLong();
        long otherCourse = postJson("/api/courses", other, Map.of("title", "الفيزياء", "price", 300)).path("summary").path("id").asLong();
        String library = mvc.perform(get("/api/learning").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(library).findValuesAsText("id")).contains(String.valueOf(course), String.valueOf(otherCourse));
        mvc.perform(get("/api/courses/" + course).header("Authorization", "Bearer " + admin).header("X-Academy-Id", aid))
            .andExpect(status().isOk()).andExpect(jsonPath("$.summary.academyId").value(aid));
        String audit = mvc.perform(get("/api/dashboard/audit").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("ACADEMY_CREATED").doesNotContain("TestPass123!");
        mvc.perform(get("/api/courses").header("Authorization", "Bearer " + teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/courses/" + otherCourse).header("Authorization", "Bearer " + teacher)).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses").header("Authorization", "Bearer " + teacher).header("X-Academy-Id", bid)).andExpect(status().isForbidden());
        mvc.perform(get("/api/academies/" + bid + "/students").header("Authorization", "Bearer " + teacher)).andExpect(status().isForbidden());
        mvc.perform(get("/api/courses").header("Authorization", "Bearer " + admin).header("X-Academy-Id", aid)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put("/api/courses/" + course + "/discount").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON).content("{\"discountPercent\":30}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(300)).andExpect(jsonPath("$.finalPrice").value(210));
        mvc.perform(put("/api/courses/" + course + "/discount").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON).content("{\"discountPercent\":101}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/academies/test-math")).andExpect(status().isOk()).andExpect(jsonPath("$.courses.length()").value(2)).andExpect(jsonPath("$.courses[0].finalPrice").value(210));
        mvc.perform(post("/api/academies/" + aid + "/students").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("fullName", "خطأ", "username", "invalid.student", "password", "TestPass123!", "courseIds", List.of(otherCourse))))).andExpect(status().isBadRequest());
        long studentId = postJson("/api/academies/" + aid + "/students", teacher, Map.of("fullName", "طالب الرياضيات", "username", "math.student", "password", "StudentPass123!", "courseIds", List.of(course))).path("id").asLong();
        String student = login("math.student", "StudentPass123!");
        mvc.perform(get("/api/courses").header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(course));
        mvc.perform(get("/api/courses/" + hidden).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/enrollments/self").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("courseId", hidden)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/checkout/orders").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("courseId", hidden)))).andExpect(status().isBadRequest()); // no online gateway: paid courses are unlocked with a code from the teacher, never by an order
        // A published page takes students into its FREE courses on its own; paid ones (`hidden` above) still need checkout or a code.
        long openCourse = postJson("/api/courses", teacher, Map.of("title", "مقدمة مجانية", "price", 0)).path("summary").path("id").asLong();
        mvc.perform(post("/api/enrollments/self").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("courseId", openCourse)))).andExpect(status().isOk());
        mvc.perform(put("/api/academies/" + aid + "/students/" + studentId).header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("courseIds", List.of())))).andExpect(status().isOk());
        mvc.perform(get("/api/courses/" + course).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/learning").header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/search").param("q", "الجبر").header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
        mvc.perform(get("/api/users/by-role/TEACHER").header("Authorization", "Bearer " + teacher)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/academies/" + aid + "/students").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("fullName", "طالب آخر", "username", "math.student", "password", "StudentPass123!", "courseIds", List.of())))).andExpect(status().isConflict());
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=");
        mvc.perform(multipart("/api/academies/" + aid + "/images/photo").file(new MockMultipartFile("file", "photo.png", "image/png", png)).header("Authorization", "Bearer " + teacher)).andExpect(status().isOk());
        mvc.perform(get("/api/public/academies/test-math/images/photo")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
        mvc.perform(multipart("/api/academies/" + aid + "/images/photo").file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())).header("Authorization", "Bearer " + teacher)).andExpect(status().isBadRequest());
        var content = json.convertValue(a, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        content.put("headline", "عنوان جديد محفوظ"); content.put("published", false);
        mvc.perform(put("/api/academies/" + aid).header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON).content(body(content))).andExpect(status().isOk());
        mvc.perform(get("/api/public/academies/test-math")).andExpect(status().isNotFound());
        // Unpublished means invite-only again: even a free course refuses self-enrollment.
        mvc.perform(post("/api/enrollments/self").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("courseId", openCourse)))).andExpect(status().isForbidden());
        content.put("published", true);
        mvc.perform(put("/api/academies/" + aid).header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON).content(body(content))).andExpect(status().isOk());
        mvc.perform(get("/api/public/academies/test-math")).andExpect(status().isOk()).andExpect(jsonPath("$.profile.headline").value("عنوان جديد محفوظ")).andExpect(jsonPath("$.profile.photoData").doesNotExist());
    }
}
