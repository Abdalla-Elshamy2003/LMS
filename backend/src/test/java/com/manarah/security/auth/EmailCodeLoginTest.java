package com.manarah.security.auth;

import com.fasterxml.jackson.databind.*;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.channel.SmtpEmailSender;
import com.manarah.org.repo.TenantRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL transactions, HTTP session policy and fake mail: no real recipient is contacted. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true", "manarah.demo.password=manarah123",
        "manarah.security.email-code.require-registration-verification=true"
})
@AutoConfigureMockMvc
class EmailCodeLoginTest {
    static final String RUN = "email-code-test-" + UUID.randomUUID();
    static final AtomicInteger IPS = new AtomicInteger();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EmailLoginCodeRepository rows;
    @Autowired EmailCodeStore store;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @MockitoBean SmtpEmailSender mail;
    final Map<String, String> inbox = new ConcurrentHashMap<>();

    @BeforeEach void mail() {
        when(mail.isDeliverable()).thenReturn(true);
        doAnswer(call -> {
            var match = Pattern.compile("(?m)^([0-9]{6})$").matcher(call.getArgument(2, String.class));
            assertThat(match.find()).isTrue();
            inbox.put(call.getArgument(0), match.group(1));
            return true;
        }).when(mail).send(anyString(), anyString(), anyString());
    }
    JsonNode call(MockHttpServletRequestBuilder req, Object body, int expected) throws Exception {
        int ip = IPS.incrementAndGet();
        req.header("X-Forwarded-For", "10.211." + ip / 250 + "." + (ip % 250 + 1));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }
    String address() { return UUID.randomUUID() + "@example.com"; }
    String request(String email, String purpose) throws Exception {
        var result = call(post("/api/auth/email-code/request"), Map.of("email", email, "purpose", purpose), 200);
        assertThat(result.path("expiresInSeconds").asInt()).isEqualTo(600);
        assertThat(result.toString()).doesNotContain(inbox.get(email));
        return result.path("challengeId").asText();
    }
    JsonNode verifyCode(String id, String email, String purpose, String code, int expected) throws Exception {
        return call(post("/api/auth/email-code/verify"), Map.of("challengeId", id, "email", email, "purpose", purpose, "code", code), expected);
    }
    String proof(String email) throws Exception {
        String id = request(email, "REGISTER");
        return verifyCode(id, email, "REGISTER", inbox.get(email), 200).path("verificationToken").asText();
    }
    Map<String, Object> signup(String email, String proof) {
        var body = new HashMap<String, Object>(Map.of("fullName", "طالب اختبار الإيميل", "email", email,
                "phone", "01011112222", "grade", "الصف الثاني الثانوي", "tenantSlug", tenants.findAll().get(0).getSlug()));
        if (proof != null) body.put("emailVerificationToken", proof);
        return body;
    }
    void allowResend(String email) {
        var row = rows.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow();
        row.setCreatedAt(Instant.now().minusSeconds(61)); rows.save(row);
    }

    @Test void registrationRequiresProofAndLoginUsesOneTimeStudentSession() throws Exception {
        String email = address();
        call(post("/api/public/register"), signup(email, null), 400);
        String token = proof(email);
        assertThat(token).hasSize(43);
        call(post("/api/public/register"), signup(address(), token), 400); // proof bound to address
        var created = call(post("/api/public/register"), signup(email, token), 200);
        assertThat(created.path("accessToken").asText()).isNotBlank();
        call(post("/api/public/register"), signup(email, token), 400); // consumed proof
        var account = users.findByEmailIgnoreCase(email).orElseThrow();
        allowResend(email);
        String id = request(email, "LOGIN"), code = inbox.get(email);
        assertThat(rows.findById(id).orElseThrow().getCodeHash()).hasSize(64).isNotEqualTo(code);
        verifyCode(id, address(), "LOGIN", code, 401);
        verifyCode(id, email, "REGISTER", code, 401);
        var session = verifyCode(id, email, "LOGIN", code, 200);
        assertThat(session.path("user").path("role").asText()).isEqualTo("STUDENT");
        assertThat(session.path("user").path("id").asLong()).isEqualTo(account.getId());
        call(get("/api/auth/me").header("Authorization", "Bearer " + session.path("accessToken").asText()), null, 200);
        verifyCode(id, email, "LOGIN", code, 401);
    }

    @Test void attemptsCommitAndExpiryAndResendInvalidateCodes() throws Exception {
        String email = address(), id = request(email, "REGISTER"), good = inbox.get(email);
        String wrong = good.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) verifyCode(id, email, "REGISTER", wrong, 401);
        assertThat(rows.findById(id).orElseThrow().getAttempts()).isEqualTo(5);
        verifyCode(id, email, "REGISTER", good, 401);
        allowResend(email);
        String next = request(email, "REGISTER"), nextCode = inbox.get(email);
        assertThat(rows.findById(id).orElseThrow().getUsedAt()).isNotNull();
        var expired = rows.findById(next).orElseThrow(); expired.setExpiresAt(Instant.now().minusSeconds(1)); rows.save(expired);
        verifyCode(next, email, "REGISTER", nextCode, 401);
    }

    @Test void failedSignupRollsBackProofConsumptionAndVerificationIsAtomic() throws Exception {
        String email = address(), id = request(email, "REGISTER"), code = inbox.get(email);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> store.verify(id, email, "REGISTER", code));
            var b = workers.submit(() -> store.verify(id, email, "REGISTER", code));
            var winners = Arrays.asList(a.get(), b.get()).stream().filter(Objects::nonNull).toList();
            assertThat(winners).hasSize(1);
            String proof = winners.get(0).verificationToken();
            var invalid = signup(email, proof); invalid.put("phone", "");
            call(post("/api/public/register"), invalid, 400);
            call(post("/api/public/register"), signup(email, proof), 200);
        }
    }

    @Test void staffUnknownAndBlockedAccountsNeverGetStudentSessions() throws Exception {
        String adminId = request("admin@manarah.io", "LOGIN");
        verifyCode(adminId, "admin@manarah.io", "LOGIN", inbox.get("admin@manarah.io"), 401);
        var admin = call(post("/api/auth/login"), Map.of("email", "admin@manarah.io", "password", "manarah123"), 200);
        assertThat(admin.path("user").path("role").asText()).isEqualTo("SUPER_ADMIN");
        String unknown = address(), unknownId = request(unknown, "LOGIN");
        verifyCode(unknownId, unknown, "LOGIN", inbox.get(unknown), 401);
        String email = address(); call(post("/api/public/register"), signup(email, proof(email)), 200);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.setBlockedAt(Instant.now()); user.setBlockedReason("سبب الحظر للاختبار"); users.save(user);
        allowResend(email);
        String id = request(email, "LOGIN");
        var rejected = verifyCode(id, email, "LOGIN", inbox.get(email), 403);
        assertThat(rejected.path("message").asText()).contains("سبب الحظر");
        assertThat(rejected.path("accessToken").isMissingNode()).isTrue();
    }

    @Test void unavailableOrFailedDeliveryCannotCreateAUsableChallengeAndRequestsAreLimited() throws Exception {
        when(mail.isDeliverable()).thenReturn(false);
        assertThat(call(get("/api/auth/email-code/status"), null, 200).path("available").asBoolean()).isFalse();
        call(post("/api/auth/email-code/request"), Map.of("email", address(), "purpose", "LOGIN"), 503);
        when(mail.isDeliverable()).thenReturn(true);
        doReturn(false).when(mail).send(anyString(), anyString(), anyString());
        String failed = address();
        call(post("/api/auth/email-code/request"), Map.of("email", failed, "purpose", "REGISTER"), 503);
        assertThat(rows.findFirstByEmailOrderByCreatedAtDesc(failed).orElseThrow().getUsedAt()).isNotNull();
        mail();
        String email = address(); request(email, "REGISTER");
        call(post("/api/auth/email-code/request"), Map.of("email", email, "purpose", "LOGIN"), 429);
        for (int i = 1; i < 5; i++) { allowResend(email); request(email, "REGISTER"); }
        allowResend(email);
        call(post("/api/auth/email-code/request"), Map.of("email", email, "purpose", "REGISTER"), 429);
    }
}
