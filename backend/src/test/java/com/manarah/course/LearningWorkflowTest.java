package com.manarah.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class LearningWorkflowTest {
    private static final String RUN = "learning-test-" + UUID.randomUUID();
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> "jdbc:sqlite:" + Path.of("target", RUN + ".db").toAbsolutePath() + "?foreign_keys=true&date_class=text&busy_timeout=5000");
        props.add("manarah.storage.root", () -> Path.of("target", RUN + "-files").toAbsolutePath().toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.manarah.course.repo.LessonRepository lessonRepository;

    String login(String email) throws Exception {
        return read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "manarah123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    JsonNode read(String body) throws Exception { return json.readTree(body); }
    JsonNode create(String path, String token, Map<String, Object> body) throws Exception {
        return read(mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void teacherUploadStudentLearningAndAdminMonitoringSharePersistedContent() throws Exception {
        String teacher = login("teacher@manarah.io"), student = login("student@manarah.io"), admin = login("admin@manarah.io");
        long courseId = create("/api/courses", teacher, Map.of("title", "اختبار رحلة التعلّم", "subject", "علوم")).path("summary").path("id").asLong();
        long moduleId = create("/api/courses/" + courseId + "/modules", teacher, Map.of("title", "الفصل الأول")).path("id").asLong();
        long lessonId = create("/api/courses/modules/" + moduleId + "/lessons", teacher, Map.of("title", "الدرس الأول", "durationMin", 5, "contentText", "شرح عربي محفوظ")).path("id").asLong();
        mvc.perform(get("/api/courses/" + courseId).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        create("/api/enrollments/self", student, Map.of("courseId", courseId));
        // Bytes 4-7 must spell "ftyp" (an MP4 file-type box) - the file content validator checks this.
        var upload = new MockMultipartFile("file", "lesson.mp4", "video/mp4", new byte[]{0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
        String key = read(mvc.perform(multipart("/api/files/upload").file(upload).param("folder", "materials").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("fileKey").asText();
        long videoId = create("/api/courses/lessons/" + lessonId + "/materials", teacher, Map.of("type", "VIDEO", "title", "شرح الفيديو", "fileKey", key)).path("id").asLong();
        create("/api/courses/lessons/" + lessonId + "/materials", teacher, Map.of("type", "PDF", "title", "مذكرة الدرس", "url", "https://example.com/lesson.pdf"));
        mvc.perform(get("/api/courses/" + courseId).header("Authorization", "Bearer " + student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].lessons[0].contentText").value("شرح عربي محفوظ"))
                .andExpect(jsonPath("$.modules[0].lessons[0].materials.length()").value(2));
        mvc.perform(get("/api/files/" + key).header("Authorization", "Bearer " + student).header("Range", "bytes=0-3"))
                .andExpect(status().isForbidden());
        JsonNode playback = create("/api/files/playback/" + videoId + "/session", student, Map.of());
        mvc.perform(get(playback.path("url").asText()).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
        mvc.perform(get(playback.path("url").asText())).andExpect(status().isUnauthorized());
        // A download manager / curl has no <video> fetch metadata: refused even with a valid ticket + session.
        mvc.perform(get(playback.path("url").asText()).header("Authorization", "Bearer " + student).header("Range", "bytes=0-3"))
                .andExpect(status().isForbidden());
        mvc.perform(get(playback.path("url").asText()).header("Authorization", "Bearer " + student).header("Range", "bytes=0-3")
                .header("Sec-Fetch-Dest", "video").header("Sec-Fetch-Mode", "no-cors"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Type", "video/mp4"));
        String firstSession = playback.path("session").asText();
        mvc.perform(post("/api/files/playback/" + videoId + "/heartbeat").header("Authorization", "Bearer " + student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"session\":\"" + firstSession + "\",\"played\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.live").value(true));
        // Opening the same account on a second device replaces the first session (one device at a time).
        JsonNode second = create("/api/files/playback/" + videoId + "/session", student, Map.of());
        org.assertj.core.api.Assertions.assertThat(second.path("replacedSessions").asInt()).isEqualTo(1);
        mvc.perform(post("/api/files/playback/" + videoId + "/heartbeat").header("Authorization", "Bearer " + student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"session\":\"" + firstSession + "\",\"played\":20}"))
                .andExpect(status().isConflict());
        mvc.perform(get(playback.path("url").asText()).header("Authorization", "Bearer " + student).header("Range", "bytes=0-3")
                .header("Sec-Fetch-Dest", "video").header("Sec-Fetch-Mode", "no-cors")).andExpect(status().isForbidden());
        mvc.perform(get(second.path("url").asText()).header("Authorization", "Bearer " + student).header("Range", "bytes=0-3")
                .header("Sec-Fetch-Dest", "video").header("Sec-Fetch-Mode", "no-cors")).andExpect(status().isPartialContent());
        mvc.perform(get("/api/learning/courses/" + courseId + "/watch-log").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].sessions").value(2)).andExpect(jsonPath("$[0].replaced").value(1));
        mvc.perform(get("/api/learning/courses/" + courseId + "/watch-log").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        playback = second;
        var scheduledLesson = lessonRepository.findById(lessonId).orElseThrow();
        scheduledLesson.setReleaseAt(java.time.Instant.now().plusSeconds(3600)); lessonRepository.saveAndFlush(scheduledLesson);
        mvc.perform(post("/api/files/playback/" + videoId + "/session").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(get(playback.path("url").asText()).header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        scheduledLesson.setReleaseAt(null); lessonRepository.saveAndFlush(scheduledLesson);
        mvc.perform(put("/api/learning/lessons/" + lessonId + "/progress").header("Authorization", "Bearer " + student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"position\":45,\"completed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        // A later pause/rewind must never erase completion.
        mvc.perform(put("/api/learning/lessons/" + lessonId + "/progress").header("Authorization", "Bearer " + student)
                .contentType(MediaType.APPLICATION_JSON).content("{\"position\":10,\"completed\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        mvc.perform(get("/api/learning/courses/" + courseId + "/progress").header("Authorization", "Bearer " + login("student@manarah.io")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].lastPosition").value(10)).andExpect(jsonPath("$[0].completed").value(true));
        for (String token : new String[]{teacher, admin}) mvc.perform(get("/api/learning/courses/" + courseId + "/learners").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].completed").value(1)).andExpect(jsonPath("$[0].lastActivity").exists());
        JsonNode library = read(mvc.perform(get("/api/learning").header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode found = null;
        for (JsonNode item : library) if (item.path("summary").path("id").asLong() == courseId) found = item;
        assertThat(found).isNotNull();
        assertThat(found.path("videoCount").asLong()).isEqualTo(1);
        assertThat(found.path("fileCount").asLong()).isEqualTo(1);
        assertThat(found.path("completedCount").asLong()).isEqualTo(1);
    }

    @Test
    void deniesWrongTeacherStudentWritesAndInvalidProgress() throws Exception {
        String teacher = login("teacher@manarah.io"), student = login("student@manarah.io"), admin = login("admin@manarah.io");
        long courseId = create("/api/courses", admin, Map.of("title", "كورس الإدارة الخاص")).path("summary").path("id").asLong();
        long moduleId = create("/api/courses/" + courseId + "/modules", admin, Map.of("title", "فصل محمي")).path("id").asLong();
        long lessonId = create("/api/courses/modules/" + moduleId + "/lessons", admin, Map.of("title", "درس محمي")).path("id").asLong();
        for (String token : new String[]{teacher, student}) {
            mvc.perform(post("/api/courses/" + courseId + "/modules").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"blocked\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/learning/courses/" + courseId + "/learners").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/learning/lessons/" + lessonId + "/progress").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON).content("{\"position\":10,\"completed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/learning/lessons/" + lessonId + "/progress").header("Authorization", "Bearer " + student).contentType(MediaType.APPLICATION_JSON).content("{\"position\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/courses/lessons/" + lessonId + "/materials").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LINK\",\"title\":\"invalid\",\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void structuredScheduleFollowsEnrollmentAndBlocksConflicts() throws Exception {
        String teacher = login("teacher@manarah.io"), student = login("student@manarah.io"), admin = login("admin@manarah.io");
        JsonNode course = create("/api/courses", teacher, Map.of("title", "جدول الفيزياء المنظم", "subject", "فيزياء"));
        long courseId = course.path("summary").path("id").asLong();
        String slotBody = json.writeValueAsString(Map.of("courseId", courseId, "title", "شرح الأسبوع", "dayOfWeek", 1,
                "startTime", "16:00", "endTime", "17:30", "deliveryMode", "ONLINE", "meetingUrl", "https://meet.example.com/class", "color", "#0f766e"));
        JsonNode slot = read(mvc.perform(post("/api/schedule").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content(slotBody)).andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("فيزياء")).andExpect(jsonPath("$.teacherName").exists())
                .andReturn().getResponse().getContentAsString());
        JsonNode beforeEnroll = read(mvc.perform(get("/api/schedule").header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(beforeEnroll.path("slots").findValuesAsText("courseId")).doesNotContain(String.valueOf(courseId));
        create("/api/enrollments/self", student, Map.of("courseId", courseId));
        JsonNode afterEnroll = read(mvc.perform(get("/api/schedule").header("Authorization", "Bearer " + student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects").isArray()).andReturn().getResponse().getContentAsString());
        assertThat(afterEnroll.path("slots").findValuesAsText("courseId")).contains(String.valueOf(courseId));
        mvc.perform(post("/api/schedule").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("courseId", courseId, "dayOfWeek", 1, "startTime", "17:00", "endTime", "18:00"))))
                .andExpect(status().isConflict());
        long adminCourseId = create("/api/courses", admin, Map.of("title", "كورس بموعد محمي", "subject", "إدارة")).path("summary").path("id").asLong();
        mvc.perform(post("/api/schedule").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("courseId", adminCourseId, "dayOfWeek", 3, "startTime", "10:00", "endTime", "11:00"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/schedule/" + slot.path("id").asLong()).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        JsonNode afterDelete = read(mvc.perform(get("/api/schedule").header("Authorization", "Bearer " + student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterDelete.path("slots").findValuesAsText("courseId")).doesNotContain(String.valueOf(courseId));
    }

    @Test
    void parentTeacherAndAdminShareAProtectedSupportConversation() throws Exception {
        String teacher = login("teacher@manarah.io"), student = login("student@manarah.io");
        String parent = login("parent@manarah.io"), admin = login("admin@manarah.io");
        long courseId = create("/api/courses", teacher, Map.of("title", "تواصل مادة الأحياء", "subject", "أحياء"))
                .path("summary").path("id").asLong();
        create("/api/enrollments/self", student, Map.of("courseId", courseId));
        JsonNode context = read(mvc.perform(get("/api/support/context").header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.students.length()").value(1))
                .andReturn().getResponse().getContentAsString());
        long childId = context.path("students").get(0).path("id").asLong();
        assertThat(context.path("courses").findValuesAsText("id")).contains(String.valueOf(courseId));

        JsonNode supportCase = create("/api/support", parent, Map.of(
                "studentId", childId, "courseId", courseId, "destination", "TEACHER",
                "category", "ACADEMIC", "priority", "HIGH", "subject", "سؤال في الواجب",
                "message", "نحتاج توضيح المطلوب في السؤال الثالث"));
        long caseId = supportCase.path("id").asLong();
        mvc.perform(get("/api/support").header("Authorization", "Bearer " + teacher)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(caseId)).andExpect(jsonPath("$[0].studentName").exists());
        mvc.perform(get("/api/support").header("Authorization", "Bearer " + admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(caseId));

        create("/api/support/" + caseId + "/messages", teacher, Map.of("message", "تم توضيح المطلوب وإرفاقه داخل الدرس"));
        mvc.perform(get("/api/support").header("Authorization", "Bearer " + parent)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING_REPLY"))
                .andExpect(jsonPath("$[0].messages.length()").value(2));
        create("/api/support/" + caseId + "/messages", parent, Map.of("message", "وصل التوضيح، شكراً"));
        mvc.perform(put("/api/support/" + caseId + "/status").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/family/finance").header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].studentId").value(childId));
        mvc.perform(get("/api/family/finance").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());

        long unrelatedCourse = create("/api/courses", admin, Map.of("title", "كورس غير مسجل", "subject", "خاص"))
                .path("summary").path("id").asLong();
        mvc.perform(post("/api/support").header("Authorization", "Bearer " + parent).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("studentId", childId, "courseId", unrelatedCourse,
                        "destination", "TEACHER", "subject", "طلب غير مسموح", "message", "لا يجب إنشاء هذا الطلب"))))
                .andExpect(status().isForbidden());
    }
}
