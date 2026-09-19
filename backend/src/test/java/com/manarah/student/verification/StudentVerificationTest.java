package com.manarah.student.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.identity.repo.UserRepository;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
})
@AutoConfigureMockMvc
class StudentVerificationTest {
    private static final String RUN = "verification-test-" + UUID.randomUUID();
    private static final String VERIFY = "/api/public/students/verify/";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> "jdbc:sqlite:" + Path.of("target", RUN + ".db").toAbsolutePath() + "?foreign_keys=true&date_class=text&busy_timeout=5000");
        props.add("manarah.storage.root", () -> Path.of("target", RUN + "-files").toAbsolutePath().toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;

    private String login(String email) throws Exception {
        var body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "manarah123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String studentPassToken() throws Exception {
        var body = mvc.perform(get("/api/gate/my-pass").header("Authorization", "Bearer " + login("student@manarah.io")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private Student demoStudent() {
        var user = users.findByEmailIgnoreCase("student@manarah.io").orElseThrow();
        return students.findByTenantIdAndUserId(user.getTenantId(), user.getId()).orElseThrow();
    }

    @Test
    void validTokenOpensWithoutLoginAndExposesOnlyWhitelistedFields() throws Exception {
        String token = studentPassToken();
        Student student = demoStudent();

        var response = mvc.perform(get(VERIFY + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andReturn().getResponse().getContentAsString();

        JsonNode body = json.readTree(response);
        Set<String> fields = new HashSet<>();
        body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).isSubsetOf("verificationStatus", "institutionName", "fullName", "studentCode",
                "grade", "gradeLevel", "educationType", "enrollmentStatus");
        assertThat(body.get("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(body.get("fullName").asText()).isEqualTo(student.getFullName());
        assertThat(body.get("studentCode").asText()).isEqualTo(student.getCode());

        // Nothing sensitive or internal may appear anywhere in the payload, including as a value.
        assertThat(response).doesNotContain(token, "passToken", "cardUid", "nationalId", "notes", "password", "tenantId", "userId");
        if (student.getPhone() != null && !student.getPhone().isBlank()) assertThat(response).doesNotContain(student.getPhone());
    }

    @Test
    void unknownAndMalformedTokensAreNotFoundNotRedirectedToLogin() throws Exception {
        String unknown = mvc.perform(get(VERIFY + "0".repeat(32))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String malformed = mvc.perform(get(VERIFY + "not-a-real-token")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Same message for both, so a caller cannot tell "well-formed but unknown" from "garbage".
        assertThat(json.readTree(unknown).get("message").asText()).isEqualTo(json.readTree(malformed).get("message").asText());
    }

    @Test
    void aGarbageBearerTokenDoesNotBreakThePublicRoute() throws Exception {
        String token = studentPassToken();
        mvc.perform(get(VERIFY + token).header("Authorization", "Bearer not.a.valid.jwt")).andExpect(status().isOk());
    }

    @Test
    void suspendedStudentIsReportedInactiveWithoutIdentity() throws Exception {
        String token = studentPassToken();
        Student student = demoStudent();
        String original = student.getStatus();
        student.setStatus("SUSPENDED");
        students.save(student);
        try {
            JsonNode body = json.readTree(mvc.perform(get(VERIFY + token)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(body.get("verificationStatus").asText()).isEqualTo("INACTIVE");
            assertThat(body.has("fullName")).isFalse();
            assertThat(body.has("studentCode")).isFalse();
        } finally {
            student.setStatus(original);
            students.save(student);
        }
    }

    @Test
    void privateStudentAndGateEndpointsStayProtected() throws Exception {
        mvc.perform(get("/api/gate/my-pass")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/gate/scan").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        // A student may hold a pass but must not be able to scan other people's.
        mvc.perform(post("/api/gate/scan").header("Authorization", "Bearer " + login("student@manarah.io"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + studentPassToken() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffScanAcceptsBareTokenScannedVerifyUrlAndLegacyGateUrl() throws Exception {
        String token = studentPassToken();
        String admin = "Bearer " + login("admin@manarah.io");
        for (String code : new String[] {token, "https://manarah.example/student/verify/" + token + "?utm=x",
                "https://manarah.example/app/gate/" + token}) {
            mvc.perform(post("/api/gate/scan").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code))))
                    .andExpect(status().isOk());
        }
    }
}
