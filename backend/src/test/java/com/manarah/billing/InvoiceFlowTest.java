package com.manarah.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.billing.gateway.FawryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** «مدفوعاتي»: invoices with the method's fee, manual approval, Fawry through the gateway, and the old path. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123",
        "manarah.public-app-url=https://droos.example"
}) @AutoConfigureMockMvc
class InvoiceFlowTest {
    static final String RUN = "invoices-" + UUID.randomUUID();
    static final String PASS = "TestPass123!", YEAR = "الصف الثالث الثانوي";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }

    /** Stands in for Fawaterak: hands out codes, and accepts a signature only when it is "signed:" + the fields. */
    static class FakeGateway implements FawryGateway {
        final AtomicInteger next = new AtomicInteger(9000);
        volatile boolean expireNext;
        @Override public boolean configured() { return true; }
        @Override public FawryCode createFawryCode(String number, BigDecimal total, Customer c, List<Item> items, String webhookUrl, String returnUrl) {
            assertThat(items.stream().map(Item::price).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
            assertThat(webhookUrl).endsWith("/api/public/payments/fawaterak/webhook_json");
            int n = next.incrementAndGet();
            Instant expires = expireNext ? Instant.now().minus(1, ChronoUnit.MINUTES) : Instant.now().plus(1, ChronoUnit.DAYS);
            expireNext = false;
            return new FawryCode(String.valueOf(n), "key" + n, "98" + n, expires);
        }
        @Override public Optional<Boolean> isPaid(String id) { return Optional.of(false); }
        @Override public boolean validPaidSignature(String id, String key, String method, String hash) { return ("signed:" + id + key + method).equals(hash); }
        @Override public boolean validCancelSignature(String ref, String method, String hash) { return ("signed:" + ref + method).equals(hash); }
    }

    @TestConfiguration static class GatewayConfig {
        @Bean @Primary FakeGateway fakeGateway() { return new FakeGateway(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FakeGateway gateway;
    @Autowired PaymentSubmissionRepository submissions;
    @Autowired com.manarah.subscription.PlanSubscriptionRepository subscriptions;

    ResultActions call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.header("X-Forwarded-For", "10.7." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }
    JsonNode ok(ResultActions r) throws Exception { return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    String token(String user, String pass) throws Exception {
        return ok(call(post("/api/auth/login"), null, Map.of("email", user, "password", pass))).path("accessToken").asText();
    }
    String register(String name, String email, String phone) throws Exception {
        return ok(call(post("/api/public/register"), null, Map.of("fullName", name, "email", email, "password", PASS,
                "phone", phone, "tenantSlug", "invoice-math"))).path("accessToken").asText();
    }

    @Test void studentsPayThroughInvoices() throws Exception {
        String admin = token("admin@manarah.io", "manarah123");
        ok(call(post("/api/academies"), admin, Map.of("name", "مستر الفواتير", "slug", "invoice-math", "username", "invoice.math", "password", PASS)));
        String teacher = token("invoice.math", PASS);
        long course = ok(call(post("/api/courses"), teacher, Map.of("title", "رياضة تالتة", "price", 100, "subject", "الرياضيات", "grade", YEAR)))
                .path("summary").path("id").asLong();
        // Lengths are 1–24 months, each once, and priced.
        for (Object bad : List.of(List.of(Map.of("months", 1, "price", 90)), List.of(Map.of("months", 30, "price", 900)), List.of(Map.of("months", 3, "price", 0))))
            call(put("/api/plans"), teacher, Map.of("year", YEAR, "subject", "الرياضيات", "price", 100, "months", 1, "extraOptions", bad))
                    .andExpect(status().isBadRequest());
        // The teacher sells the year for a month (100) or six months (500).
        long plan = ok(call(put("/api/plans"), teacher, Map.of("year", YEAR, "subject", "الرياضيات", "price", 100, "months", 1,
                "extraOptions", List.of(Map.of("months", 6, "price", 500))))).path("id").asLong();
        String one = register("طالب أول", "pay.one@example.com", "01011114444"), two = register("طالب تاني", "pay.two@example.com", "01011115555");

        var options = ok(call(get("/api/me/payment-options"), one, null));
        var choice = options.path("plans").path(0);
        assertThat(choice.path("planId").asLong()).isEqualTo(plan);
        assertThat(choice.path("options").findValuesAsText("months")).containsExactly("1", "6");
        assertThat(options.path("methods").findValuesAsText("code")).contains("VODAFONE_CASH", "INSTAPAY", "FAWRY");

        // Vodafone Cash adds 10%: 100 + 10 = 110, and pressing twice gives the same invoice.
        var vodafone = ok(call(post("/api/me/invoices"), one, Map.of("planId", plan, "months", 1, "method", "VODAFONE_CASH")));
        assertThat(vodafone.path("baseAmount").decimalValue()).isEqualByComparingTo("100");
        assertThat(vodafone.path("feeAmount").decimalValue()).isEqualByComparingTo("10");
        assertThat(vodafone.path("total").decimalValue()).isEqualByComparingTo("110");
        assertThat(vodafone.path("account").asText()).isEqualTo("01115978493");
        long invoice = vodafone.path("id").asLong();
        assertThat(ok(call(post("/api/me/invoices"), one, Map.of("planId", plan, "months", 1, "method", "VODAFONE_CASH"))).path("id").asLong()).isEqualTo(invoice);
        // Nobody else sees it.
        call(get("/api/me/invoices/" + invoice), two, null).andExpect(status().isNotFound());

        // The screenshot goes to head office, who approves it: the month starts and the course opens.
        mvc.perform(multipart("/api/me/invoices/" + invoice + "/receipt").param("reference", "VF-123").header("Authorization", "Bearer " + one))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_REVIEW"));
        var queue = ok(call(get("/api/admin/payments"), admin, null));
        JsonNode submission = null;
        for (JsonNode p : queue) if (vodafone.path("number").asText().equals(p.path("invoiceNumber").asText())) submission = p;
        assertThat(submission).isNotNull();
        assertThat(submission.path("amount").decimalValue()).isEqualByComparingTo("110");
        ok(call(post("/api/admin/payments/" + submission.path("id").asLong() + "/approve"), admin, null));
        var paid = ok(call(get("/api/me/invoices/" + invoice), one, null));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        Instant monthEnds = Instant.parse(paid.path("periodEndsAt").asText());
        assertThat(monthEnds).isBetween(Instant.now().plus(27, ChronoUnit.DAYS), Instant.now().plus(32, ChronoUnit.DAYS));
        call(get("/api/courses/" + course), one, null).andExpect(status().isOk());
        call(get("/api/courses/" + course), two, null).andExpect(status().isForbidden());

        // Renewing for six months by Fawry: a reference code, no fee; a forged webhook changes nothing, a real one pays
        // once however often it repeats, and the six months start when the current month ends.
        var fawry = ok(call(post("/api/me/invoices"), one, Map.of("planId", plan, "months", 6, "method", "FAWRY")));
        assertThat(fawry.path("fawryCode").asText()).startsWith("98");
        assertThat(fawry.path("total").decimalValue()).isEqualByComparingTo("500");
        String gid = String.valueOf(gateway.next.get()), gkey = "key" + gid;
        call(post("/api/public/payments/fawaterak/webhook_json"), null, Map.of("invoice_id", gid, "invoice_key", gkey,
                "payment_method", "Fawry", "invoice_status", "paid", "hashKey", "forged")).andExpect(status().isUnauthorized());
        assertThat(ok(call(get("/api/me/invoices/" + fawry.path("id").asLong()), one, null)).path("status").asText()).isEqualTo("UNPAID");
        var webhook = Map.of("invoice_id", gid, "invoice_key", gkey, "payment_method", "Fawry", "invoice_status", "paid",
                "referenceNumber", "REF1", "hashKey", "signed:" + gid + gkey + "Fawry");
        call(post("/api/public/payments/fawaterak/webhook_json"), null, webhook).andExpect(jsonPath("$.status").value("paid"));
        call(post("/api/public/payments/fawaterak/webhook_json"), null, webhook).andExpect(jsonPath("$.status").value("already_paid"));
        var renewed = ok(call(get("/api/me/invoices/" + fawry.path("id").asLong()), one, null));
        assertThat(renewed.path("status").asText()).isEqualTo("PAID");
        assertThat(Instant.parse(renewed.path("periodEndsAt").asText())).isAfter(monthEnds.plus(170, ChronoUnit.DAYS));
        long fawryPayments = 0;
        for (JsonNode p : ok(call(get("/api/admin/payments"), admin, null)))
            if ("FAWRY".equals(p.path("method").asText()) && "APPROVED".equals(p.path("status").asText())) fawryPayments++;
        assertThat(fawryPayments).isEqualTo(1);

        // A Fawry code that ran out can be issued again.
        gateway.expireNext = true;
        var late = ok(call(post("/api/me/invoices"), two, Map.of("planId", plan, "months", 1, "method", "FAWRY")));
        assertThat(ok(call(get("/api/me/invoices/" + late.path("id").asLong()), two, null)).path("status").asText()).isEqualTo("EXPIRED");
        var fresh = ok(call(post("/api/me/invoices/" + late.path("id").asLong() + "/renew"), two, null));
        assertThat(fresh.path("status").asText()).isEqualTo("UNPAID");
        assertThat(fresh.path("fawryCode").asText()).isNotEqualTo(late.path("fawryCode").asText());

        // A student who only sent the screenshot on WhatsApp: head office finds the invoice by its number and confirms it.
        String three = register("طالب تالت", "pay.three@example.com", "01011116666");
        var insta = ok(call(post("/api/me/invoices"), three, Map.of("planId", plan, "months", 6, "method", "INSTAPAY")));
        assertThat(insta.path("total").decimalValue()).isEqualByComparingTo("500");
        assertThat(insta.path("planId").asLong()).isEqualTo(plan);
        String typed = " " + insta.path("number").asText().substring(3).toLowerCase() + " ";
        call(get("/api/admin/invoices/lookup").param("number", typed), teacher, null).andExpect(status().isForbidden());
        var found = ok(call(get("/api/admin/invoices/lookup").param("number", typed), admin, null));
        assertThat(found.path("id").asLong()).isEqualTo(insta.path("id").asLong());
        assertThat(found.path("studentName").asText()).isEqualTo("طالب تالت");
        call(post("/api/admin/invoices/" + fawry.path("id").asLong() + "/confirm"), admin, Map.of("reference", "x")).andExpect(status().isBadRequest());
        var confirmed = ok(call(post("/api/admin/invoices/" + found.path("id").asLong() + "/confirm"), admin, Map.of("reference", "IPN-55")));
        assertThat(confirmed.path("status").asText()).isEqualTo("PAID");
        assertThat(Instant.parse(confirmed.path("periodEndsAt").asText())).isAfter(Instant.now().plus(170, ChronoUnit.DAYS));
        call(post("/api/admin/invoices/" + found.path("id").asLong() + "/confirm"), admin, Map.of()).andExpect(status().isConflict());
        call(get("/api/courses/" + course), three, null).andExpect(status().isOk());
        assertThat(ok(call(get("/api/admin/payments/summary"), admin, null)).path("total").decimalValue()).isEqualByComparingTo("1110");

        // A receipt sent before invoices existed (none attached) can still be approved, and starts the subscription.
        String four = register("طالب رابع", "pay.four@example.com", "01011117777");
        ok(call(post("/api/me/plans/" + plan + "/request"), four, null));
        long legacySub = 0;
        for (JsonNode s : ok(call(get("/api/plans/requests"), teacher, null))) if (s.path("studentName").asText().equals("طالب رابع")) legacySub = s.path("id").asLong();
        PaymentSubmission legacy = new PaymentSubmission();
        var subRow = subscriptions.findById(legacySub).orElseThrow();
        legacy.setTenantId(subRow.getTenantId()); legacy.setStudentId(subRow.getStudentId()); legacy.setPlanSubscriptionId(legacySub);
        legacy.setMethodCode("VODAFONE_CASH"); legacy.setAmount(new BigDecimal("100")); legacy.setReference("OLD-1");
        long legacyId = submissions.save(legacy).getId();
        ok(call(post("/api/admin/payments/" + legacyId + "/approve"), admin, null));
        call(get("/api/courses/" + course), four, null).andExpect(status().isOk());

        // Changing the way to pay: the same subscription and length by InstaPay is a new invoice, and the Fawry one closes.
        var switched = ok(call(post("/api/me/invoices"), two, Map.of("planId", plan, "months", 1, "method", "INSTAPAY")));
        assertThat(switched.path("id").asLong()).isNotEqualTo(late.path("id").asLong());
        assertThat(switched.path("total").decimalValue()).isEqualByComparingTo("100");
        assertThat(ok(call(get("/api/me/invoices/" + late.path("id").asLong()), two, null)).path("status").asText()).isEqualTo("CANCELLED");

        // The older pay screen goes through an invoice too: Vodafone Cash still costs 110 there.
        long pending = 0;
        for (JsonNode s : ok(call(get("/api/plans/requests"), teacher, null))) if (s.path("studentName").asText().equals("طالب تاني")) pending = s.path("id").asLong();
        assertThat(pending).isPositive();
        mvc.perform(multipart("/api/me/payments").param("subscriptionId", String.valueOf(pending)).param("method", "VODAFONE_CASH")
                .param("reference", "VF-9").header("Authorization", "Bearer " + two)).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(110.0));
    }
}
