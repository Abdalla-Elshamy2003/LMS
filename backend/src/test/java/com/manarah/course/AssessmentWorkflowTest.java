package com.manarah.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.exam.repo.StudentExamRepository;
import com.manarah.identity.repo.UserRepository;
import com.manarah.student.repo.StudentRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
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
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=", "manarah.demo.seed-enabled=true", "manarah.demo.password=manarah123", "manarah.exams.expiry-interval-ms=3600000"})
@AutoConfigureMockMvc
class AssessmentWorkflowTest {
    private static final String RUN="assessment-"+UUID.randomUUID();
    @DynamicPropertySource static void config(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
        p.add("manarah.storage.root",()->Path.of("target",RUN+"-files").toAbsolutePath().toString());
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired StudentRepository students; @Autowired EnrollmentRepository enrollments; @Autowired StudentExamRepository attempts;
    String login(String email) throws Exception { return postJson("/api/auth/login",null,Map.of("email",email,"password","manarah123")).path("accessToken").asText(); }
    JsonNode postJson(String path,String token,Object data) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
        if(token!=null) request.header("Authorization","Bearer "+token);
        return json.readTree(mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
    long course() {
        var u=users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        var s=students.findByTenantIdAndUserId(u.getTenantId(),u.getId()).orElseThrow();
        return enrollments.findByTenantIdAndStudentId(u.getTenantId(),s.getId()).getFirst().getCourseId();
    }
    @Test void homeworkUploadRevisionGradingAndOwnershipAreEnforced() throws Exception {
        String admin=login("admin@manarah.io"), student=login("student@manarah.io");
        long a=postJson("/api/homework/assignments",admin,Map.of("courseId",course(),"title","Assessment acceptance","maxScore",20,"deadline",Instant.now().plusSeconds(86400).toString())).path("id").asLong();
        mvc.perform(post("/api/homework/submit").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("assignmentId",a,"text","  ")))).andExpect(status().isBadRequest());
        String foreign=json.readTree(mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","answer.pdf","application/pdf","%PDF-1.4\n".getBytes())).param("folder","submissions").header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("fileKey").asText();
        mvc.perform(post("/api/homework/submit").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("assignmentId",a,"fileKey",foreign)))).andExpect(status().isForbidden());
        String own=json.readTree(mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file","answer.pdf","application/pdf","%PDF-1.4\n".getBytes())).param("folder","submissions").header("Authorization","Bearer "+student)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("fileKey").asText();
        long submission=postJson("/api/homework/submit",student,Map.of("assignmentId",a,"fileKey",own)).path("id").asLong();
        mvc.perform(get("/api/files/"+own).header("Authorization","Bearer "+student)).andExpect(status().isOk());
        assertThat(postJson("/api/homework/submissions/"+submission+"/return",admin,Map.of("feedback","Show the working")).path("status").asText()).isEqualTo("RETURNED");
        assertThat(postJson("/api/homework/submit",student,Map.of("assignmentId",a,"text","Updated working","fileKey",own)).path("id").asLong()).isEqualTo(submission);
        mvc.perform(post("/api/homework/submissions/"+submission+"/grade").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"score\":21}")).andExpect(status().isBadRequest());
        assertThat(postJson("/api/homework/submissions/"+submission+"/grade",admin,Map.of("score",0,"feedback","Review the method")).path("score").asDouble()).isZero();
        mvc.perform(post("/api/homework/submit").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("assignmentId",a,"text","Cannot overwrite grade")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/homework/assignments/"+a+"/mark-missing").header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
    }
    @Test void draftsResumeAndManualGradesWaitForEveryEssay() throws Exception {
        String admin=login("admin@manarah.io"), student=login("student@manarah.io");
        long exam=postJson("/api/exams",admin,Map.of("courseId",course(),"title","Two essays","durationMinutes",30)).path("summary").path("id").asLong();
        List<Long> ids=new ArrayList<>();
        for(int i=0;i<2;i++) {
            long q=postJson("/api/exams/questions",admin,Map.of("type","ESSAY","difficulty","EASY","stem","Explain "+i,"points",10)).path("id").asLong(); ids.add(q);
            postJson("/api/exams/"+exam+"/questions",admin,Map.of("questionId",q));
        }
        mvc.perform(post("/api/exams/"+exam+"/publish").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        long attempt=postJson("/api/exams/"+exam+"/start",student,Map.of()).path("studentExamId").asLong();
        Object body=Map.of("answers",ids.stream().map(q->Map.of("questionId",q,"answerText","My explanation")).toList());
        mvc.perform(put("/api/exams/attempts/"+attempt+"/draft").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.savedAt").exists());
        JsonNode resumed=postJson("/api/exams/"+exam+"/start",student,Map.of());
        assertThat(resumed.path("savedAnswers").size()).isEqualTo(2); assertThat(resumed.path("studentExamId").asLong()).isEqualTo(attempt);
        mvc.perform(put("/api/exams/attempts/"+attempt+"/draft").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"questionId\":999999,\"answerText\":\"x\"}]}")).andExpect(status().isBadRequest());
        assertThat(postJson("/api/exams/attempts/"+attempt+"/submit",student,body).path("needsManualGrade").asBoolean()).isTrue();
        mvc.perform(post("/api/exams/attempts/"+attempt+"/grade").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("questionId",ids.get(0),"points",7)))).andExpect(status().isOk());
        assertThat(attempts.findById(attempt).orElseThrow().isNeedsManualGrade()).isTrue();
        mvc.perform(post("/api/exams/attempts/"+attempt+"/grade").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("questionId",ids.get(1),"points",8)))).andExpect(status().isOk());
        assertThat(attempts.findById(attempt).orElseThrow().isNeedsManualGrade()).isFalse();
        assertThat(postJson("/api/exams/attempts/"+attempt+"/submit",student,body).path("score").asDouble()).isEqualTo(15);
        mvc.perform(get("/api/exams/attempts/"+attempt+"/answers").header("Authorization","Bearer "+student)).andExpect(status().isForbidden());
    }
    @Test void expiredAttemptsUseSavedDraftInsteadOfLateChanges() throws Exception {
        String admin=login("admin@manarah.io"), student=login("student@manarah.io");
        long exam=postJson("/api/exams",admin,Map.of("courseId",course(),"title","Expiry","durationMinutes",1)).path("summary").path("id").asLong();
        long q=postJson("/api/exams/questions",admin,Map.of("type","SHORT_ANSWER","difficulty","EASY","stem","2+2","points",10,"correctAnswer","4")).path("id").asLong();
        postJson("/api/exams/"+exam+"/questions",admin,Map.of("questionId",q));
        mvc.perform(post("/api/exams/"+exam+"/publish").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        long a=postJson("/api/exams/"+exam+"/start",student,Map.of()).path("studentExamId").asLong();
        mvc.perform(put("/api/exams/attempts/"+a+"/draft").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",q,"answerText","4")))))).andExpect(status().isOk());
        var attempt=attempts.findById(a).orElseThrow(); attempt.setStartedAt(Instant.now().minusSeconds(120)); attempts.saveAndFlush(attempt);
        Object late=Map.of("answers",List.of(Map.of("questionId",q,"answerText","wrong")));
        mvc.perform(put("/api/exams/attempts/"+a+"/draft").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(late))).andExpect(status().isBadRequest());
        assertThat(postJson("/api/exams/attempts/"+a+"/submit",student,late).path("score").asDouble()).isEqualTo(10);
    }
}
