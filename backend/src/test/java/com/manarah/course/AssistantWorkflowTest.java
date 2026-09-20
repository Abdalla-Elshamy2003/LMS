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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A teacher's assistant: created by the teacher inside their own academy, signs in with an email, does everything
 * the teacher does day to day, cannot touch the teacher's own decisions (assistants, money, publishing), cannot see
 * another teacher's academy, and works through tasks / follow-up notes / the daily desk.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class AssistantWorkflowTest {
    static final String RUN = "assistant-test-" + UUID.randomUUID();
    static final String PASS = "AssistPass123!";

    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String body(Object value) throws Exception { return json.writeValueAsString(value); }

    ResultActions send(MockHttpServletRequestBuilder request, String token, Object payload) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (payload != null) request.contentType(MediaType.APPLICATION_JSON).content(body(payload));
        return mvc.perform(request);
    }
    JsonNode postJson(String path, String token, Object value) throws Exception {
        return json.readTree(send(post(path), token, value).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path, String token) throws Exception {
        return json.readTree(send(get(path), token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String login(String identifier, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", identifier, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }

    @Test void assistantWorksForTheirTeacherOnlyAndUsesTheAssistantWorkspace() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        var a = postJson("/api/academies", admin, Map.of("name", "مدرس الكيمياء", "slug", "assist-chem", "username", "assist.chem", "password", "TeacherPass123!"));
        var b = postJson("/api/academies", admin, Map.of("name", "مدرس الأحياء", "slug", "assist-bio", "username", "assist.bio", "password", "TeacherPass123!"));
        long aid = a.path("id").asLong(), bid = b.path("id").asLong();
        String teacher = login("assist.chem", "TeacherPass123!"), otherTeacher = login("assist.bio", "TeacherPass123!");
        long teacherId = a.path("teacherId").asLong();
        long course = postJson("/api/courses", teacher, Map.of("title", "كيمياء عضوية", "price", 200)).path("summary").path("id").asLong();
        long otherCourse = postJson("/api/courses", otherTeacher, Map.of("title", "أحياء", "price", 200)).path("summary").path("id").asLong();
        long studentId = postJson("/api/academies/" + aid + "/students", teacher,
                Map.of("fullName", "طالب الكيمياء", "username", "chem.student", "password", "StudentPass123!", "courseIds", List.of(course))).path("id").asLong();
        String student = login("chem.student", "StudentPass123!");

        // ---- The teacher creates the assistant; only the teacher (or the managing admin) can. ----
        Map<String, Object> newAssistant = Map.of("fullName", "مساعد الكيمياء", "email", "Helper.Chem@Example.com", "phone", "01000000001", "password", PASS);
        send(post("/api/academies/" + aid + "/assistants"), teacher, Map.of("fullName", "x", "email", "not-an-email", "password", PASS)).andExpect(status().isBadRequest());
        send(post("/api/academies/" + aid + "/assistants"), teacher, Map.of("fullName", "x", "email", "weak@example.com", "password", "123")).andExpect(status().isBadRequest());
        send(post("/api/academies/" + bid + "/assistants"), teacher, newAssistant).andExpect(status().isForbidden());
        send(post("/api/academies/" + aid + "/assistants"), student, newAssistant).andExpect(status().isForbidden());
        var created = postJson("/api/academies/" + aid + "/assistants", teacher, newAssistant);
        long assistantId = created.path("id").asLong();
        assertThat(created.path("email").asText()).isEqualTo("helper.chem@example.com");
        assertThat(created.toString()).doesNotContain(PASS).doesNotContain("passwordHash");
        send(post("/api/academies/" + aid + "/assistants"), teacher, newAssistant).andExpect(status().isConflict());
        send(post("/api/academies/" + bid + "/assistants"), otherTeacher, newAssistant).andExpect(status().isConflict()); // an email is one login, platform-wide
        assertThat(getJson("/api/academies/" + aid + "/assistants", teacher)).hasSize(1);
        assertThat(getJson("/api/academies/" + aid + "/assistants", admin)).hasSize(1);
        send(get("/api/academies/" + bid + "/assistants"), teacher, null).andExpect(status().isForbidden());

        String assistant = login("Helper.Chem@Example.com", PASS);   // sign-in is by email, any letter case
        send(get("/api/auth/me"), assistant, null).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.roleArabic").value("مساعد مدرس"));

        // ---- Everything the teacher does day to day, the assistant does too, for that teacher. ----
        assertThat(getJson("/api/academies", assistant)).hasSize(1);
        assertThat(getJson("/api/courses", assistant).findValuesAsText("id")).containsExactly(String.valueOf(course));
        assertThat(getJson("/api/learning", assistant).findValuesAsText("id")).contains(String.valueOf(course));
        var made = postJson("/api/courses", assistant, Map.of("title", "كيمياء تحليلية", "price", 150));
        long madeCourse = made.path("summary").path("id").asLong();
        assertThat(made.path("summary").path("teacherId").asLong()).as("a course made by the assistant belongs to the teacher").isEqualTo(teacherId);
        long module = postJson("/api/courses/" + madeCourse + "/modules", assistant, Map.of("title", "الباب الأول")).path("id").asLong();
        postJson("/api/courses/modules/" + module + "/lessons", assistant, Map.of("title", "الدرس الأول"));
        assertThat(getJson("/api/dashboard/teacher", assistant).path("courses").asInt()).isEqualTo(2);
        postJson("/api/exams", assistant, Map.of("courseId", course, "title", "امتحان المساعد", "durationMinutes", 20));
        postJson("/api/schedule", assistant, Map.of("courseId", course, "dayOfWeek", 2, "startTime", "17:00", "endTime", "18:30"));
        long secondStudent = postJson("/api/academies/" + aid + "/students", assistant,
                Map.of("fullName", "طالب ثان", "username", "chem.student2", "password", "StudentPass123!", "courseIds", List.of(course))).path("id").asLong();
        send(put("/api/academies/" + aid + "/students/" + secondStudent), assistant, Map.of("courseIds", List.of())).andExpect(status().isOk());
        send(get("/api/students"), assistant, null).andExpect(status().isOk());
        send(get("/api/students/" + studentId), assistant, null).andExpect(status().isOk());

        // ---- ...but not the teacher's own decisions. ----
        send(put("/api/academies/" + aid + "/credentials"), assistant, Map.of("username", "hijack.chem", "password", PASS)).andExpect(status().isForbidden());
        send(post("/api/academies/" + aid + "/assistants"), assistant, Map.of("fullName", "y", "email", "another@example.com", "password", PASS)).andExpect(status().isForbidden());
        send(get("/api/academies/" + aid + "/assistants"), assistant, null).andExpect(status().isForbidden());
        send(put("/api/academies/" + aid + "/assistants/" + assistantId), assistant, Map.of("active", false)).andExpect(status().isForbidden());
        var content = json.convertValue(a, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        content.put("instapayNumber", "01011111111"); content.put("published", true);
        send(put("/api/academies/" + aid), teacher, content).andExpect(status().isOk());
        content.put("headline", "عنوان يعدّله المساعد");
        send(put("/api/academies/" + aid), assistant, content).andExpect(status().isOk());        // page text: fine
        content.put("instapayNumber", "01099999999");
        send(put("/api/academies/" + aid), assistant, content).andExpect(status().isForbidden()); // where money goes: teacher only
        content.put("instapayNumber", "01011111111"); content.put("published", false);
        send(put("/api/academies/" + aid), assistant, content).andExpect(status().isForbidden()); // publishing: teacher only

        // ---- Another teacher's academy is out of reach. ----
        send(get("/api/academies/" + bid + "/students"), assistant, null).andExpect(status().isForbidden());
        send(get("/api/courses/" + otherCourse), assistant, null).andExpect(status().isNotFound());
        send(get("/api/courses").header("X-Academy-Id", bid), assistant, null).andExpect(status().isForbidden());
        send(post("/api/academies/" + bid + "/students"), assistant, Map.of("fullName", "z", "username", "z.student", "password", "StudentPass123!", "courseIds", List.of())).andExpect(status().isForbidden());
        assertThat(getJson("/api/assistant/tasks", otherTeacher)).isEmpty();

        // ---- Students and parents never reach the assistant workspace. ----
        send(get("/api/assistant/desk"), student, null).andExpect(status().isForbidden());
        send(get("/api/assistant/tasks"), student, null).andExpect(status().isForbidden());
        send(get("/api/assistant/notes"), student, null).andExpect(status().isForbidden());

        // ---- Tasks: teacher hands work over; assistant picks it up and closes it. ----
        var second = postJson("/api/academies/" + aid + "/assistants", teacher, Map.of("fullName", "مساعد ثان", "email", "second.chem@example.com", "password", PASS));
        String assistant2 = login("second.chem@example.com", PASS);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        send(post("/api/assistant/tasks"), teacher, Map.of("title", "x", "assignedTo", second.path("id").asLong() + 999)).andExpect(status().isBadRequest());
        send(post("/api/assistant/tasks"), teacher, Map.of("title", "x", "assignedTo", teacherId)).andExpect(status().isBadRequest()); // a teacher is not an assistant
        send(post("/api/assistant/tasks"), teacher, Map.of("title", "x", "studentId", 999999)).andExpect(status().isBadRequest());
        send(post("/api/assistant/tasks"), teacher, Map.of("title", " ")).andExpect(status().isBadRequest());
        long mine = postJson("/api/assistant/tasks", teacher, Map.of("title", "اتصل بولي أمر طالب الكيمياء", "priority", "HIGH", "dueDate", yesterday.toString(),
                "studentId", studentId, "courseId", course, "assignedTo", assistantId)).path("id").asLong();
        long open = postJson("/api/assistant/tasks", teacher, Map.of("title", "جهّز ورقة المراجعة")).path("id").asLong();
        JsonNode myTasks = getJson("/api/assistant/tasks", assistant);
        assertThat(myTasks).hasSize(2);
        assertThat(myTasks.findValuesAsText("title")).contains("اتصل بولي أمر طالب الكيمياء");
        JsonNode first = null; for (JsonNode t : myTasks) if (t.path("id").asLong() == mine) first = t;
        assertThat(first.path("overdue").asBoolean()).isTrue();
        assertThat(first.path("studentName").asText()).isEqualTo("طالب الكيمياء");
        assertThat(first.path("assignedToName").asText()).isEqualTo("مساعد الكيمياء");
        assertThat(getJson("/api/assistant/tasks", assistant2)).hasSize(1);                                   // only the unassigned one
        send(put("/api/assistant/tasks/" + mine + "/status"), assistant2, Map.of("status", "DONE")).andExpect(status().isForbidden());
        send(put("/api/assistant/tasks/" + mine + "/status"), assistant, Map.of("status", "BOGUS")).andExpect(status().isBadRequest());
        send(put("/api/assistant/tasks/" + open + "/status"), assistant2, Map.of("status", "DOING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value(second.path("id").asLong()));                       // picking it up claims it
        assertThat(getJson("/api/assistant/tasks", assistant)).hasSize(1);
        send(put("/api/assistant/tasks/" + mine + "/status"), assistant, Map.of("status", "DONE", "note", "تم الاتصال وتأكيد الحضور")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.overdue").value(false));
        var teacherView = getJson("/api/assistant/tasks", teacher);
        assertThat(teacherView).hasSize(2);
        assertThat(teacherView.findValuesAsText("resultNote")).contains("تم الاتصال وتأكيد الحضور");
        // Only the teacher hands out work: an assistant can neither create nor delete tasks.
        send(post("/api/assistant/tasks"), assistant, Map.of("title", "مهمة كتبتها لنفسي")).andExpect(status().isForbidden());
        send(delete("/api/assistant/tasks/" + mine), assistant, null).andExpect(status().isForbidden());
        assertThat(getJson("/api/assistant/tasks", assistant2).findValuesAsText("title")).doesNotContain("مهمة كتبتها لنفسي");
        send(delete("/api/assistant/tasks/" + mine), teacher, null).andExpect(status().isOk());

        // ---- Follow-up notes: private to the teacher's staff. ----
        send(post("/api/assistant/notes"), assistant, Map.of("studentId", studentId, "body", " ")).andExpect(status().isBadRequest());
        send(post("/api/assistant/notes"), assistant, Map.of("studentId", 999999, "body", "x")).andExpect(status().isBadRequest());
        send(post("/api/assistant/notes"), assistant, Map.of("studentId", studentId, "kind", "NOPE", "body", "x")).andExpect(status().isBadRequest());
        long note = postJson("/api/assistant/notes", assistant, Map.of("studentId", studentId, "kind", "CALL_PARENT",
                "body", "ولي الأمر طلب موعداً بعد الحصة", "followUpOn", LocalDate.now().toString())).path("id").asLong();
        JsonNode seenByTeacher = getJson("/api/assistant/notes?studentId=" + studentId, teacher);
        assertThat(seenByTeacher).hasSize(1);
        assertThat(seenByTeacher.get(0).path("authorName").asText()).isEqualTo("مساعد الكيمياء");
        assertThat(seenByTeacher.get(0).path("due").asBoolean()).isTrue();
        send(get("/api/assistant/notes?studentId=" + studentId), otherTeacher, null).andExpect(status().isBadRequest()); // not their student
        send(delete("/api/assistant/notes/" + note), assistant2, null).andExpect(status().isForbidden());

        // ---- The desk gathers what is waiting today. ----
        JsonNode desk = getJson("/api/assistant/desk", assistant);
        assertThat(desk.path("counts").path("followUpsDue").asInt()).isEqualTo(1);
        assertThat(desk.path("counts").fieldNames()).toIterable().contains("homeworkToGrade", "examsToReview", "openSupport",
                "openTasks", "overdueTasks", "absentToday");
        assertThat(desk.path("followUps").get(0).path("studentName").asText()).isEqualTo("طالب الكيمياء");
        send(get("/api/assistant/desk"), teacher, null).andExpect(status().isOk());
        send(put("/api/assistant/notes/" + note + "/status"), assistant, Map.of("status", "RESOLVED")).andExpect(status().isOk());
        assertThat(getJson("/api/assistant/desk", assistant).path("counts").path("followUpsDue").asInt()).isZero();
        send(delete("/api/assistant/notes/" + note), assistant, null).andExpect(status().isOk());

        // ---- An assistant in a school tenant (several teachers, nobody to act for) keeps the old read-only reach. ----
        long branch = getJson("/api/org/branches", admin).get(0).path("id").asLong();
        send(post("/api/users"), admin, Map.of("fullName", "مساعد مدرسة", "email", "school.assistant@example.com", "password", PASS,
                "role", "ASSISTANT", "branchId", branch)).andExpect(status().isOk());
        String schoolAssistant = login("school.assistant@example.com", PASS);
        long schoolCourse = 0;
        for (JsonNode c : getJson("/api/courses", admin)) if (c.path("academyId").isNull() || c.path("academyId").isMissingNode()) { schoolCourse = c.path("id").asLong(); break; }
        assertThat(schoolCourse).as("the seeded school tenant has a course of its own").isPositive();
        send(get("/api/courses/" + schoolCourse), schoolAssistant, null).andExpect(status().isOk());
        send(post("/api/courses/" + schoolCourse + "/modules"), schoolAssistant, Map.of("title", "لا")).andExpect(status().isForbidden());
        send(post("/api/schedule"), schoolAssistant, Map.of("courseId", schoolCourse, "dayOfWeek", 3, "startTime", "17:00", "endTime", "18:00")).andExpect(status().isForbidden());

        // ---- Suspending or removing an assistant shuts the login and frees the email. ----
        send(put("/api/academies/" + aid + "/assistants/" + assistantId), teacher, Map.of("active", false)).andExpect(status().isOk());
        send(get("/api/courses"), assistant, null).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "helper.chem@example.com", "password", PASS))))
                .andExpect(status().isUnauthorized());
        send(put("/api/academies/" + aid + "/assistants/" + assistantId), teacher, Map.of("active", true, "password", "NewAssist456!")).andExpect(status().isOk());
        login("helper.chem@example.com", "NewAssist456!");
        send(delete("/api/academies/" + aid + "/assistants/" + assistantId), teacher, null).andExpect(status().isOk());
        assertThat(getJson("/api/academies/" + aid + "/assistants", teacher)).hasSize(1);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "helper.chem@example.com", "password", "NewAssist456!"))))
                .andExpect(status().isUnauthorized());
        postJson("/api/academies/" + aid + "/assistants", teacher, newAssistant);                             // the freed address can be reused
        // Audit rows live in the academy's own tenant, so the managing admin reads them through that academy.
        String audit = json.readTree(send(get("/api/dashboard/audit").header("X-Academy-Id", aid), admin, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).toString();
        assertThat(audit).contains("ACADEMY_ASSISTANT_CREATED", "ACADEMY_ASSISTANT_REMOVED").doesNotContain(PASS);
    }
}
