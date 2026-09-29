package com.manarah.ops;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The uptime probes, the browser's monitoring config, and head office's system status and test email. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class OpsEndpointsTest {
    static final String RUN = "ops-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void probesStatusAndTestEmail() throws Exception {
        mvc.perform(get("/api/public/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        // No storage configured here, so there is no fresh backup: the probe must say so.
        mvc.perform(get("/api/public/health/backup")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("STALE"));
        mvc.perform(get("/api/public/client-config")).andExpect(status().isOk()).andExpect(jsonPath("$.sentryDsn").value(""));

        mvc.perform(get("/api/admin/system/status")).andExpect(status().isUnauthorized());
        String admin = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "admin@manarah.io", "password", "manarah123"))))
                .andReturn().getResponse().getContentAsString()).path("accessToken").asText();
        mvc.perform(get("/api/admin/system/status").header("Authorization", "Bearer " + admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email.ready").value(false))
                .andExpect(jsonPath("$.backups.configured").value(false))
                .andExpect(jsonPath("$.monitoring.backend").value(false));
        // Email is off in tests: the button says exactly what is missing instead of pretending it sent.
        mvc.perform(post("/api/admin/system/test-email").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"owner@example.com\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false)).andExpect(jsonPath("$.error").isNotEmpty());
        mvc.perform(post("/api/admin/system/test-email").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"not-an-email\"}")).andExpect(status().isBadRequest());
    }
}
