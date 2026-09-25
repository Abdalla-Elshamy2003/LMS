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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The platform takes the money: head office sets up Vodafone Cash, the student pays a subscription through it and sends
 * the reference with a receipt photo, head office sends it back once with a reason, then approves — which starts the
 * subscription. Receipts stay private; only head office reviews.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class PlatformPaymentTest {
    static final String RUN = "pay-test-" + UUID.randomUUID();
    static final String PASSWORD = "StudentPass123!", FIRST = "الصف الأول الثانوي";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.header("X-Forwarded-For", "10.6." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var res = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
        String out = res.getContentType() != null && res.getContentType().startsWith("image/") ? "" : res.getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }
    static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    MockHttpServletRequestBuilder pay(long subscriptionId, String method, String reference, boolean withReceipt) throws Exception {
        var req = multipart("/api/me/payments");
        if (withReceipt) req.file(new MockMultipartFile("receipt", "receipt.png", "image/png", png()));
        req.param("subscriptionId", String.valueOf(subscriptionId)).param("method", method)
                .param("reference", reference).param("sender", "01011112222");
        return req;
    }

    @Test void payThePlatformAndHeadOfficeStartsTheSubscription() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        // Nothing to pay with until head office sets a method up — and it can't be switched on empty.
        assertThat(call(get("/api/public/payment-methods"), null, null, 200).size()).isZero();
        call(put("/api/admin/payment-methods/VODAFONE_CASH"), admin, Map.of("enabled", true, "account", ""), 400);
        call(put("/api/admin/payment-methods/VODAFONE_CASH"), admin, Map.of("enabled", true, "account", "01000000000",
                "accountName", "منصة منارة", "instructions", "حوّل واكتب رقم العملية"), 200);
        JsonNode methods = call(get("/api/public/payment-methods"), null, null, 200);
        assertThat(methods.findValuesAsText("code")).containsExactly("VODAFONE_CASH");

        call(post("/api/academies"), admin, Map.of("name", "مستر فيزياء", "slug", "pay-physics", "username", "pay.physics",
                "password", "TeachPass123!"), 200);
        String teacher = login("pay.physics", "TeachPass123!");
        call(get("/api/admin/payments"), teacher, null, 403);
        long motion = call(post("/api/courses"), teacher, Map.of("title", "الحركة", "price", 200, "subject", "الفيزياء", "grade", FIRST), 200)
                .path("summary").path("id").asLong();
        call(put("/api/plans"), teacher, Map.of("year", FIRST, "subject", "الفيزياء", "price", 300, "months", 2), 200);

        String student = call(post("/api/public/register"), null, Map.of("fullName", "طالب دافع", "email", "payer@example.com",
                "password", PASSWORD, "phone", "01011112222", "tenantSlug", "pay-physics", "courseId", motion), 200).path("accessToken").asText();
        long subscription = call(get("/api/me/catalog"), student, null, 200).path("plans").get(0).path("pendingId").asLong();

        // Only a method head office turned on; only the student's own request; a reference or a receipt.
        call(pay(subscription, "INSTAPAY", "TX1", false), student, null, 400);
        call(pay(subscription, "VODAFONE_CASH", "", false), student, null, 400);
        String other = call(post("/api/public/register"), null, Map.of("fullName", "طالب تاني", "email", "other.payer@example.com",
                "password", PASSWORD, "phone", "01011113333", "tenantSlug", "pay-physics"), 200).path("accessToken").asText();
        call(pay(subscription, "VODAFONE_CASH", "TX1", false), other, null, 403);

        JsonNode sent = call(pay(subscription, "VODAFONE_CASH", "TX-777", true), student, null, 200);
        assertThat(sent.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(sent.path("amount").asDouble()).isEqualTo(300.0);
        JsonNode plan = call(get("/api/me/catalog"), student, null, 200).path("plans").get(0);
        assertThat(plan.path("paymentStatus").asText()).isEqualTo("SUBMITTED");
        assertThat(call(get("/api/plans/requests"), teacher, null, 200).get(0).path("paymentStatus").asText()).isEqualTo("SUBMITTED");

        // Head office sees it with the receipt; the receipt is private to head office and the student.
        JsonNode waiting = call(get("/api/admin/payments").param("status", "SUBMITTED"), admin, null, 200);
        assertThat(waiting.size()).isEqualTo(1);
        long paymentId = waiting.get(0).path("id").asLong();
        assertThat(waiting.get(0).path("hasReceipt").asBoolean()).isTrue();
        assertThat(waiting.get(0).path("email").asText()).isEqualTo("payer@example.com");
        call(get("/api/admin/payments/" + paymentId + "/receipt"), admin, null, 200);
        call(get("/api/me/payments/" + paymentId + "/receipt"), student, null, 200);
        call(get("/api/me/payments/" + paymentId + "/receipt"), other, null, 403);

        // Sent back once with a reason; the student sends again.
        call(post("/api/admin/payments/" + paymentId + "/reject"), admin, Map.of("reason", ""), 400);
        call(post("/api/admin/payments/" + paymentId + "/reject"), admin, Map.of("reason", "رقم العملية مش ظاهر"), 200);
        plan = call(get("/api/me/catalog"), student, null, 200).path("plans").get(0);
        assertThat(plan.path("paymentStatus").asText()).isEqualTo("REJECTED");
        assertThat(plan.path("paymentNote").asText()).isEqualTo("رقم العملية مش ظاهر");
        call(get("/api/courses/" + motion), student, null, 403);
        long again = call(pay(subscription, "VODAFONE_CASH", "TX-778", false), student, null, 200).path("id").asLong();

        // Approved: the subscription starts and the year's courses open.
        call(post("/api/admin/payments/" + again + "/approve"), teacher, null, 403);
        call(post("/api/admin/payments/" + again + "/approve"), admin, null, 200);
        call(post("/api/admin/payments/" + again + "/approve"), admin, null, 409);
        call(get("/api/courses/" + motion), student, null, 200);
        assertThat(call(get("/api/me/subscriptions"), student, null, 200).get(0).path("status").asText()).isEqualTo("ACTIVE");
        JsonNode summary = call(get("/api/admin/payments/summary"), admin, null, 200);
        assertThat(summary.path("total").asDouble()).isEqualTo(300.0);
        assertThat(summary.path("waiting").asLong()).isZero();
        assertThat(summary.path("teachers").get(0).path("teacher").asText()).isEqualTo("مستر فيزياء");
    }
}
