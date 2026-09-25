package com.manarah.course;

import com.fasterxml.jackson.databind.*;
import com.manarah.subscription.PlanAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Subscribing by year and subject with a teacher: "أولى ثانوي فيزياء" for the months the teacher set opens every
 * physics course of that year — including one published later — until the period ends; an early renewal runs on from
 * the current end; the end locks exactly what the subscription opened (a course bought with its own code stays open,
 * one the teacher closed stays closed); codes and cancelling work the same way.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123",
        "manarah.subscriptions.expiry-interval-ms=86400000"
}) @AutoConfigureMockMvc
class YearSubscriptionTest {
    static final String RUN = "plans-test-" + UUID.randomUUID();
    static final String PASSWORD = "StudentPass123!", FIRST = "الصف الأول الثانوي", SECOND = "الصف الثاني الثانوي", PHYSICS = "الفيزياء";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlanAccess access;

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.header("X-Forwarded-For", "10.8." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }
    long course(String token, String title, int price, String year) throws Exception {
        return call(post("/api/courses"), token, Map.of("title", title, "price", price, "subject", PHYSICS, "grade", year), 200)
                .path("summary").path("id").asLong();
    }
    Map<Long, String> states(String token) throws Exception {
        Map<Long, String> out = new HashMap<>();
        call(get("/api/me/catalog"), token, null, 200).path("courses").forEach(c -> out.put(c.path("id").asLong(), c.path("state").asText()));
        return out;
    }
    JsonNode mySubscription(String token) throws Exception {
        JsonNode list = call(get("/api/me/subscriptions"), token, null, 200);
        assertThat(list.size()).isEqualTo(1);
        return list.get(0);
    }

    @Test void subscribeByYearAndSubjectForTheTeachersMonths() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        long academyId = call(post("/api/academies"), admin, Map.of("name", "مستر محمد", "slug", "pl-physics",
                "username", "pl.physics", "password", "TeachPass123!"), 200).path("id").asLong();
        String teacher = login("pl.physics", "TeachPass123!");
        long motion = course(teacher, "الحركة", 200, FIRST), newton = course(teacher, "قوانين نيوتن", 150, FIRST);
        long electricity = course(teacher, "الكهربية", 200, SECOND);

        // The teacher sees a row for each year their courses cover, and prices أولى ثانوي فيزياء: 300, 10% off, 2 months.
        JsonNode rows = call(get("/api/plans"), teacher, null, 200);
        assertThat(rows.findValuesAsText("year")).contains(FIRST, SECOND);
        call(put("/api/plans"), teacher, Map.of("year", FIRST, "subject", PHYSICS, "price", 0, "months", 2), 400);
        JsonNode plan = call(put("/api/plans"), teacher, Map.of("year", FIRST, "subject", PHYSICS, "price", 300,
                "discountPercent", 10, "months", 2), 200);
        long planId = plan.path("id").asLong();
        assertThat(plan.path("finalPrice").asDouble()).isEqualTo(270.0);
        assertThat(json.convertValue(plan.path("courses"), List.class)).contains("الحركة", "قوانين نيوتن").doesNotContain("الكهربية");

        // Signing up from "الحركة" asks for the year's subscription; nothing opens until the teacher is paid.
        String student = call(post("/api/public/register"), null, Map.of("fullName", "طالب أولى", "email", "first.year@example.com",
                "password", PASSWORD, "phone", "01000000011", "tenantSlug", "pl-physics", "courseId", motion), 200).path("accessToken").asText();
        Map<Long, String> seen = states(student);
        assertThat(seen.get(motion)).isEqualTo("PENDING");
        assertThat(seen.get(newton)).isEqualTo("PENDING");
        assertThat(seen).doesNotContainKey(electricity);
        JsonNode planInfo = call(get("/api/me/catalog"), student, null, 200).path("plans").get(0);
        assertThat(planInfo.path("status").asText()).isEqualTo("PENDING");
        assertThat(planInfo.path("months").asInt()).isEqualTo(2);
        call(get("/api/courses/" + motion), student, null, 403);

        // "قوانين نيوتن" bought on its own with a course code: not the subscription's to close later.
        String newtonCode = call(post("/api/courses/" + newton + "/access-codes"), teacher, Map.of("count", 1), 200).get(0).path("code").asText();
        call(post("/api/courses/redeem-code"), student, Map.of("code", newtonCode), 200);

        // The teacher got paid: activate. Every أولى ثانوي physics course opens, for two months from today.
        JsonNode requests = call(get("/api/plans/requests"), teacher, null, 200);
        assertThat(requests.size()).isEqualTo(1);
        assertThat(requests.get(0).path("email").asText()).isEqualTo("first.year@example.com");
        call(post("/api/plan-subscriptions/" + requests.get(0).path("id").asLong() + "/activate"), student, null, 403);
        call(post("/api/plan-subscriptions/" + requests.get(0).path("id").asLong() + "/activate"), teacher, null, 200);
        call(get("/api/courses/" + motion), student, null, 200);
        call(get("/api/courses/" + electricity), student, null, 403);
        JsonNode mine = mySubscription(student);
        assertThat(mine.path("status").asText()).isEqualTo("ACTIVE");
        Instant starts = Instant.parse(mine.path("startsAt").asText()), ends = Instant.parse(mine.path("endsAt").asText());
        assertThat(ends).isEqualTo(ZonedDateTime.ofInstant(starts, PlanAccess.CAIRO).plusMonths(2).toInstant());
        assertThat(mine.path("daysLeft").asLong()).isBetween(58L, 62L);
        assertThat(mine.path("teacher").asText()).isEqualTo("مستر محمد");

        // A course the teacher publishes later for أولى ثانوي فيزياء is open for them straight away.
        long waves = course(teacher, "الموجات", 180, FIRST);
        call(get("/api/courses/" + waves), student, null, 200);

        // Paid again before the end: the new period runs on from the current end, not from today.
        call(post("/api/plans/" + planId + "/students/" + studentId(teacher, planId) + "/renew"), teacher, null, 200);
        Instant extended = Instant.parse(mySubscription(student).path("endsAt").asText());
        assertThat(extended).isEqualTo(ZonedDateTime.ofInstant(ends, PlanAccess.CAIRO).plusMonths(2).toInstant());

        // The teacher closes "الموجات" for this student in their student editor.
        call(put("/api/academies/" + academyId + "/students/" + studentId(teacher, planId)), admin,
                Map.of("courseIds", List.of(motion, newton)), 200);
        call(get("/api/courses/" + waves), student, null, 403);

        // Past the first period the renewal keeps everything open; past the second, the subscription's courses lock.
        access.expire(Instant.now().plus(Duration.ofDays(70)));
        call(get("/api/courses/" + motion), student, null, 200);
        access.expire(Instant.now().plus(Duration.ofDays(130)));
        call(get("/api/courses/" + motion), student, null, 403);
        call(get("/api/courses/" + newton), student, null, 200);          // its own code: stays open
        assertThat(states(student).get(motion)).isEqualTo("EXPIRED");
        assertThat(mySubscription(student).path("status").asText()).isEqualTo("ENDED");
        assertThat(call(get("/api/learning"), student, null, 200).findValuesAsText("title")).doesNotContain("الحركة");

        // Renewing: the student asks, the teacher activates — open again, except what the teacher closed.
        call(post("/api/me/plans/" + planId + "/request"), student, null, 200);
        assertThat(states(student).get(motion)).isEqualTo("PENDING");
        call(post("/api/plan-subscriptions/" + call(get("/api/plans/requests"), teacher, null, 200).get(0).path("id").asLong() + "/activate"), teacher, null, 200);
        call(get("/api/courses/" + motion), student, null, 200);
        call(get("/api/courses/" + waves), student, null, 403);

        // A subscription code: signing up with it starts a period in the plan's year straight away.
        String code = call(post("/api/plans/" + planId + "/codes"), teacher, Map.of("count", 1), 200).get(0).path("code").asText();
        assertThat(code).startsWith("SUB-");
        String second = call(post("/api/public/redeem-code"), null, Map.of("code", code, "fullName", "طالب بكود", "email", "code.student@example.com",
                "password", PASSWORD, "phone", "01000000012"), 200).path("accessToken").asText();
        assertThat(call(get("/api/me/catalog"), second, null, 200).path("grade").asText()).isEqualTo(FIRST);
        call(get("/api/courses/" + motion), second, null, 200);
        call(post("/api/public/redeem-code"), null, Map.of("code", code, "fullName", "تاني", "email", "again@example.com",
                "password", PASSWORD, "phone", "01000000013"), 409);

        // The teacher stops the second student: locked at once.
        JsonNode subscribers = call(get("/api/plans/" + planId + "/subscribers"), teacher, null, 200);
        long secondSub = 0;
        for (JsonNode s : subscribers) if ("طالب بكود".equals(s.path("studentName").asText())) secondSub = s.path("subscriptionId").asLong();
        call(post("/api/plan-subscriptions/" + secondSub + "/cancel"), teacher, null, 200);
        call(get("/api/courses/" + motion), second, null, 403);
    }

    long studentId(String teacher, long planId) throws Exception {
        for (JsonNode s : call(get("/api/plans/" + planId + "/subscribers"), teacher, null, 200))
            if ("طالب أولى".equals(s.path("studentName").asText())) return s.path("studentId").asLong();
        throw new AssertionError("subscriber not found");
    }
}
