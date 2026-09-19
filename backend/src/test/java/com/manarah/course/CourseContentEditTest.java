package com.manarah.course;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
})
@AutoConfigureMockMvc
class CourseContentEditTest {
    private static final String RUN = "content-edit-test-" + UUID.randomUUID();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        com.manarah.TestDatabase.register(props, RUN);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String email) throws Exception {
        var body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "manarah123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private JsonNode create(String path, String token, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void teacherRenamesEditsAndDeletesChaptersAndLessonsEvenWithStudentProgress() throws Exception {
        String teacher = login("teacher@manarah.io"), student = login("student@manarah.io");
        long course = create("/api/courses", teacher, Map.of("title", "كورس التعديل")).path("summary").path("id").asLong();
        long module = create("/api/courses/" + course + "/modules", teacher, Map.of("title", "الفصل الأول")).path("id").asLong();
        long lesson = create("/api/courses/modules/" + module + "/lessons", teacher, Map.of("title", "الدرس الأول", "durationMin", 5)).path("id").asLong();
        create("/api/courses/lessons/" + lesson + "/materials", teacher, Map.of("type", "LINK", "title", "رابط", "url", "https://example.com/a"));
        long keptLesson = create("/api/courses/modules/" + module + "/lessons", teacher, Map.of("title", "درس باقٍ")).path("id").asLong();

        mvc.perform(post("/api/enrollments/self").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":" + course + "}")).andExpect(status().isOk());
        mvc.perform(put("/api/learning/lessons/" + lesson + "/progress").header("Authorization", "Bearer " + student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"position\":10,\"completed\":true}")).andExpect(status().isOk());

        mvc.perform(put("/api/courses/modules/" + module).header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"فصل معدّل\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("فصل معدّل"));
        mvc.perform(put("/api/courses/lessons/" + lesson).header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"درس معدّل\",\"durationMin\":42,\"contentText\":\"شرح جديد\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("درس معدّل"))
                .andExpect(jsonPath("$.durationMin").value(42)).andExpect(jsonPath("$.materials.length()").value(1));
        mvc.perform(put("/api/courses/lessons/" + lesson).header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \"}")).andExpect(status().isBadRequest());

        mvc.perform(delete("/api/courses/lessons/" + lesson).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/courses/lessons/" + lesson).header("Authorization", "Bearer " + teacher)).andExpect(status().isOk());
        var afterLesson = json.readTree(mvc.perform(get("/api/courses/" + course).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterLesson.path("modules").get(0).path("lessons").size()).isEqualTo(1);
        assertThat(afterLesson.path("modules").get(0).path("lessons").get(0).path("id").asLong()).isEqualTo(keptLesson);
        mvc.perform(delete("/api/courses/lessons/" + lesson).header("Authorization", "Bearer " + teacher)).andExpect(status().isNotFound());

        mvc.perform(delete("/api/courses/modules/" + module).header("Authorization", "Bearer " + teacher)).andExpect(status().isOk());
        mvc.perform(get("/api/courses/" + course).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modules.length()").value(0));
    }

    @Test
    void anotherTeachersCourseContentCannotBeEditedOrDeleted() throws Exception {
        String owner = login("teacher@manarah.io"), other = login("sara@manarah.io");
        long course = create("/api/courses", owner, Map.of("title", "كورس مدرس آخر")).path("summary").path("id").asLong();
        long module = create("/api/courses/" + course + "/modules", owner, Map.of("title", "فصل")).path("id").asLong();
        long lesson = create("/api/courses/modules/" + module + "/lessons", owner, Map.of("title", "درس")).path("id").asLong();
        mvc.perform(put("/api/courses/modules/" + module).header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/courses/modules/" + module).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/courses/lessons/" + lesson).header("Authorization", "Bearer " + other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/courses/" + course).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modules[0].lessons.length()").value(1));
    }
}
