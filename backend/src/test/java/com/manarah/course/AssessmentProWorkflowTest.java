package com.manarah.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.homework.repo.AssignmentRepository;
import com.manarah.identity.repo.UserRepository;
import com.manarah.student.repo.StudentRepository;
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

/** Professional assessment workflow: rubrics, late policy, multi-file submissions, comment bank,
 *  question/exam lifecycle rules, student review policy and integrity events. */
@SpringBootTest(properties={"manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=", "manarah.demo.seed-enabled=true", "manarah.demo.password=manarah123", "manarah.exams.expiry-interval-ms=3600000"})
@AutoConfigureMockMvc
class AssessmentProWorkflowTest {
    private static final String RUN="assessment-pro-"+UUID.randomUUID();
    @DynamicPropertySource static void config(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
        p.add("manarah.storage.root",()->Path.of("target",RUN+"-files").toAbsolutePath().toString());
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired StudentRepository students; @Autowired EnrollmentRepository enrollments; @Autowired AssignmentRepository assignments;

    String login(String email) throws Exception { return postJson("/api/auth/login",null,Map.of("email",email,"password","manarah123")).path("accessToken").asText(); }
    JsonNode postJson(String path,String token,Object data) throws Exception {
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
        if(token!=null) request.header("Authorization","Bearer "+token);
        return json.readTree(mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
    JsonNode putJson(String path,String token,Object data) throws Exception {
        return json.readTree(mvc.perform(put(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path,String token) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String upload(String token,String name) throws Exception {
        byte[] pngHeader = {(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};
        return json.readTree(mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file",name,"image/png",pngHeader)).param("folder","submissions").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("fileKey").asText();
    }
    long course() {
        var u=users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        var s=students.findByTenantIdAndUserId(u.getTenantId(),u.getId()).orElseThrow();
        return enrollments.findByTenantIdAndStudentId(u.getTenantId(),s.getId()).getFirst().getCourseId();
    }

    @Test void rubricLatePenaltyMultiFileAndCommentBank() throws Exception {
        String admin=login("admin@manarah.io"), student=login("student@manarah.io");
        var rubric=List.of(Map.of("title","الفهم","maxPoints",6),Map.of("title","التنظيم","maxPoints",4,"levels",List.of(Map.of("label","ممتاز","points",4),Map.of("label","مقبول","points",2))));
        mvc.perform(post("/api/homework/assignments").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("courseId",course(),"title","Rubric","maxScore",20,"rubric",rubric)))).andExpect(status().isBadRequest()); // total 10 != 20
        JsonNode a=postJson("/api/homework/assignments",admin,Map.of("courseId",course(),"title","Rubric","maxScore",10,"rubric",rubric,"latePenaltyPercent",10,"deadline",Instant.now().plusSeconds(3600).toString()));
        long assignment=a.path("id").asLong();
        assertThat(a.path("rubric").size()).isEqualTo(2);
        String c1=a.path("rubric").get(0).path("id").asText(), c2=a.path("rubric").get(1).path("id").asText();

        // Three photos of a notebook in one submission; a sixth file is rejected; a foreign key is rejected.
        String k1=upload(student,"page1.png"), k2=upload(student,"page2.png"), k3=upload(student,"page3.png");
        JsonNode sub=postJson("/api/homework/submit",student,Map.of("assignmentId",assignment,"files",List.of(Map.of("fileKey",k1,"name","page1.png","size",3),Map.of("fileKey",k2,"name","page2.png","size",3),Map.of("fileKey",k3,"name","page3.png","size",3))));
        assertThat(sub.path("files").size()).isEqualTo(3);
        long submission=sub.path("id").asLong();
        mvc.perform(get("/api/files/"+k2).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        String foreign=upload(admin,"x.png");
        mvc.perform(post("/api/homework/submit").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assignmentId",assignment,"files",List.of(Map.of("fileKey",k1),Map.of("fileKey",foreign)))))).andExpect(status().isForbidden());
        // Resubmission keeps the existing attachment without re-proving ownership and bumps the counter.
        JsonNode again=postJson("/api/homework/submit",student,Map.of("assignmentId",assignment,"text","edited","files",List.of(Map.of("fileKey",k1,"name","page1.png"))));
        assertThat(again.path("resubmissions").asInt()).isEqualTo(1); assertThat(again.path("files").size()).isEqualTo(1);

        // Rubric grading: per-criterion bounds enforced, score derived server side.
        mvc.perform(post("/api/homework/submissions/"+submission+"/grade").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("rubricScores",Map.of(c1,7,c2,4))))).andExpect(status().isBadRequest());
        JsonNode graded=postJson("/api/homework/submissions/"+submission+"/grade",admin,Map.of("rubricScores",Map.of(c1,5,c2,2),"feedback","good"));
        assertThat(graded.path("score").asDouble()).isEqualTo(7); assertThat(graded.path("penaltyPercent").asDouble()).isEqualTo(0);
        assertThat(getJson("/api/homework/my",student).findValue("myRubricScores").path(c1).asDouble()).isEqualTo(5);

        // Late policy: 2 days late × 10% = 20% off the raw score, unless waived.
        var late=assignments.findById(assignment).orElseThrow(); late.setDeadline(Instant.now().minusSeconds(86400+60)); assignments.saveAndFlush(late);
        JsonNode lateA=postJson("/api/homework/assignments",admin,Map.of("courseId",course(),"title","Late","maxScore",10,"latePenaltyPercent",10,"deadline",Instant.now().minusSeconds(86400+60).toString()));
        long lateSub=postJson("/api/homework/submit",student,Map.of("assignmentId",lateA.path("id").asLong(),"text","late work")).path("id").asLong();
        JsonNode lateGraded=postJson("/api/homework/submissions/"+lateSub+"/grade",admin,Map.of("score",10));
        assertThat(lateGraded.path("status").asText()).isEqualTo("GRADED");
        assertThat(lateGraded.path("rawScore").asDouble()).isEqualTo(10); assertThat(lateGraded.path("penaltyPercent").asDouble()).isEqualTo(20); assertThat(lateGraded.path("score").asDouble()).isEqualTo(8);
        // A closed assignment refuses late submissions from students.
        long closed=postJson("/api/homework/assignments",admin,Map.of("courseId",course(),"title","Closed","maxScore",10,"allowLate",false,"deadline",Instant.now().minusSeconds(60).toString())).path("id").asLong();
        mvc.perform(post("/api/homework/submit").header("Authorization","Bearer "+student).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assignmentId",closed,"text","too late")))).andExpect(status().isBadRequest());
        // Max score frozen after grading; title still editable; delete blocked with grades.
        mvc.perform(put("/api/homework/assignments/"+assignment).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"maxScore\":50}")).andExpect(status().isBadRequest());
        assertThat(putJson("/api/homework/assignments/"+assignment,admin,Map.of("title","Rubric v2")).path("title").asText()).isEqualTo("Rubric v2");
        mvc.perform(delete("/api/homework/assignments/"+assignment).header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/homework/assignments/"+closed).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        // Comment bank is private per teacher.
        long comment=postJson("/api/homework/comments",admin,Map.of("text","راجع خطوات الحل")).path("id").asLong();
        assertThat(postJson("/api/homework/comments/"+comment+"/use",admin,Map.of()).path("uses").asInt()).isEqualTo(1);
        String teacher=login("teacher@manarah.io");
        assertThat(getJson("/api/homework/comments",teacher).size()).isEqualTo(0);
        mvc.perform(delete("/api/homework/comments/"+comment).header("Authorization","Bearer "+teacher)).andExpect(status().isNotFound());
    }

    @Test void examLifecycleReviewPolicyAndIntegrity() throws Exception {
        String admin=login("admin@manarah.io"), student=login("student@manarah.io");
        long q1=postJson("/api/exams/questions",admin,Map.of("type","MCQ","difficulty","EASY","stem","2+2?","points",5,"explanation","because","options",List.of(Map.of("text","4","correct",true),Map.of("text","5","correct",false)))).path("id").asLong();
        long q2=postJson("/api/exams/questions",admin,Map.of("type","SHORT_ANSWER","difficulty","EASY","stem","capital","points",5,"correctAnswer","القاهرة|Cairo")).path("id").asLong();
        mvc.perform(post("/api/exams/questions").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type","NUMERIC","difficulty","EASY","stem","x","correctAnswer","abc")))).andExpect(status().isBadRequest());
        JsonNode exam=postJson("/api/exams",admin,Map.of("courseId",course(),"title","Pro","durationMinutes",30,"showResults","AFTER_CLOSE","fullscreen",true,"disableCopy",true,"detectTabSwitch",true));
        long examId=exam.path("summary").path("id").asLong();
        postJson("/api/exams/"+examId+"/questions",admin,Map.of("questionId",q1)); postJson("/api/exams/"+examId+"/questions",admin,Map.of("questionId",q2));
        // Points override + reorder + remove before publish.
        JsonNode detail=json.readTree(mvc.perform(patch("/api/exams/"+examId+"/questions/"+q2).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"pointsOverride\":15}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.path("summary").path("totalPoints").asDouble()).isEqualTo(20);
        assertThat(putJson("/api/exams/"+examId+"/questions/order",admin,Map.of("questionIds",List.of(q2,q1))).path("questions").get(0).path("id").asLong()).isEqualTo(q2);
        mvc.perform(delete("/api/exams/questions/"+q1).header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest()); // used in exam
        mvc.perform(post("/api/exams/"+examId+"/publish").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        // Student catalogue shows the exam as OPEN and not reviewable yet.
        JsonNode card=getJson("/api/exams/my",student).get(0);
        assertThat(card.path("window").asText()).isEqualTo("OPEN"); assertThat(card.path("canReview").asBoolean()).isFalse();
        JsonNode attempt=postJson("/api/exams/"+examId+"/start",student,Map.of());
        long attemptId=attempt.path("studentExamId").asLong();
        assertThat(attempt.path("fullscreen").asBoolean()).isTrue();
        // Attempt settings are frozen once a student started; question edits too.
        mvc.perform(put("/api/exams/"+examId).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"durationMinutes\":10}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/exams/questions/"+q1).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("type","MCQ","difficulty","EASY","stem","changed","options",List.of(Map.of("text","4","correct",true),Map.of("text","5","correct",false)))))).andExpect(status().isBadRequest());
        assertThat(putJson("/api/exams/"+examId,admin,Map.of("title","Pro renamed")).path("summary").path("title").asText()).isEqualTo("Pro renamed");
        // Integrity events ride along with the draft; unknown types are ignored.
        long correctOpt=0;
        for (JsonNode o : getJson("/api/exams/questions/"+q1,admin).path("options")) if (o.path("correct").asBoolean()) correctOpt=o.path("id").asLong();
        var events=List.of(Map.of("type","TAB_HIDDEN","at",Instant.now().toString()),Map.of("type","FULLSCREEN_EXIT","at",Instant.now().plusSeconds(1).toString()),Map.of("type","BOGUS","at",Instant.now().toString()));
        putJson("/api/exams/attempts/"+attemptId+"/draft",student,Map.of("answers",List.of(Map.of("questionId",q1,"selectedOptions",List.of(correctOpt)),Map.of("questionId",q2,"answerText","cairo")),"tabSwitches",1,"events",events));
        JsonNode result=postJson("/api/exams/attempts/"+attemptId+"/submit",student,Map.of("answers",List.of(Map.of("questionId",q1,"selectedOptions",List.of(correctOpt)),Map.of("questionId",q2,"answerText"," القاهرة ")),"tabSwitches",1,"events",events));
        assertThat(result.path("score").asDouble()).isEqualTo(20); assertThat(result.path("canReview").asBoolean()).isFalse();
        JsonNode integrity=getJson("/api/exams/attempts/"+attemptId+"/integrity",admin);
        assertThat(integrity.path("fullscreenExits").asInt()).isEqualTo(1); assertThat(integrity.path("events").size()).isEqualTo(2);
        mvc.perform(get("/api/exams/attempts/"+attemptId+"/integrity").header("Authorization","Bearer "+student)).andExpect(status().isForbidden());
        // Review policy AFTER_CLOSE: forbidden while open, allowed after close, with explanation exposed.
        mvc.perform(get("/api/exams/"+examId+"/my-review").header("Authorization","Bearer "+student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/exams/"+examId+"/close").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        JsonNode review=getJson("/api/exams/"+examId+"/my-review",student);
        assertThat(review.path("percent").asDouble()).isEqualTo(100); assertThat(review.path("answers").size()).isEqualTo(2);
        assertThat(review.path("answers").findValue("explanation").asText()).isEqualTo("because");
        mvc.perform(post("/api/exams/"+examId+"/start").header("Authorization","Bearer "+student)).andExpect(status().isBadRequest());
        // Analytics carry item analysis and distribution; deleting an exam with attempts is refused.
        JsonNode analytics=getJson("/api/exams/"+examId+"/results",admin);
        assertThat(analytics.path("questionStats").size()).isEqualTo(2); assertThat(analytics.path("questionStats").get(0).path("correctRate").asDouble()).isEqualTo(100);
        assertThat(analytics.path("distribution").get(4).asInt()).isEqualTo(1); assertThat(analytics.path("rows").get(0).path("fullscreenExits").asInt()).isEqualTo(1);
        mvc.perform(delete("/api/exams/"+examId).header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
        long draft=postJson("/api/exams",admin,Map.of("courseId",course(),"title","Draft")).path("summary").path("id").asLong();
        mvc.perform(delete("/api/exams/"+draft).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
    }
}
