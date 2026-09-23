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
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Teacher packages: the shipped package, head-office-only management, academy linking, and what the public sees. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class TeacherBundleWorkflowTest {
    static final String RUN = "bundle-test-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String body(Object value) throws Exception { return json.writeValueAsString(value); }
    String login(String username, String password) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", username, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token, Object value, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (value != null) req.contentType(MediaType.APPLICATION_JSON).content(body(value));
        String out = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank() ? null : json.readTree(out);
    }
    static Map<String, Object> member(Long academyId, String subject, String photo, String video) {
        Map<String, Object> m = new HashMap<>();
        m.put("academyId", academyId); m.put("subject", subject); m.put("photoUrl", photo); m.put("introVideoUrl", video); m.put("displayName", "");
        return m;
    }
    static Map<String, Object> bundle(String slug, boolean published, List<Map<String, Object>> members) {
        return Map.of("slug", slug, "name", "باقة " + slug, "tagline", "سطر", "description", "وصف", "published", published, "members", members);
    }

    @Test void packagesAreManagedByHeadOfficeAndShowOnlyPublishedTeachers() throws Exception {
        // The migration ships one package with the five portraits, no teacher spaces linked yet.
        var cards = call(get("/api/public/bundles"), null, null, 200);
        assertThat(cards.findValuesAsText("slug")).contains("excellence");
        var shipped = call(get("/api/public/bundles/excellence"), null, null, 200);
        assertThat(shipped.path("members")).hasSize(5);
        assertThat(shipped.path("members").get(0).path("photoUrl").asText()).isEqualTo("/images/bundles/arabic.jpg");
        assertThat(shipped.path("members").get(0).hasNonNull("teacher")).isFalse();
        assertThat(call(get("/api/public/home"), null, null, 200).path("bundles").findValuesAsText("slug")).contains("excellence");

        String admin = login("admin@manarah.io", "manarah123");
        long physics = call(post("/api/academies"), admin, Map.of("name", "مستر الفيزياء", "slug", "bundle-physics", "username", "bundle.physics", "password", "TestPass123!"), 200).path("id").asLong();
        String teacher = login("bundle.physics", "TestPass123!");

        // Teachers never manage packages, not even to read the admin list.
        call(get("/api/bundles"), teacher, null, 403);
        call(post("/api/bundles"), teacher, bundle("mine", true, List.of(member(null, "الفيزياء", "", ""))), 403);

        var list = call(get("/api/bundles"), admin, null, 200);
        long excellence = 0;
        for (var b : list) if (b.path("slug").asText().equals("excellence")) excellence = b.path("id").asLong();
        assertThat(excellence).isPositive();

        // Link the physics member to its teacher space: the public page now carries that teacher's page and name.
        var members = new ArrayList<Map<String, Object>>();
        for (var s : List.of("اللغة العربية", "اللغة الإنجليزية", "الفيزياء", "العلوم", "الكيمياء"))
            members.add(member(s.equals("الفيزياء") ? physics : null, s, "/images/bundles/physics.jpg", s.equals("الفيزياء") ? "https://youtu.be/abc123" : ""));
        call(put("/api/bundles/" + excellence), admin, bundle("excellence", true, members), 200);
        var landing = call(get("/api/public/bundles/excellence"), null, null, 200);
        var linked = landing.path("members").get(2);
        assertThat(linked.path("teacher").path("slug").asText()).isEqualTo("bundle-physics");
        assertThat(linked.path("name").asText()).isEqualTo("مستر الفيزياء");
        assertThat(linked.path("introVideoUrl").asText()).isEqualTo("https://youtu.be/abc123");
        assertThat(landing.path("members").get(0).hasNonNull("teacher")).isFalse();

        // Validation: a teacher twice, an unknown space, a bad photo link, a taken link.
        var twice = new ArrayList<>(List.of(member(physics, "الفيزياء", "", ""), member(physics, "الفيزياء", "", "")));
        call(post("/api/bundles"), admin, bundle("twice", true, twice), 400);
        call(post("/api/bundles"), admin, bundle("ghost", true, List.of(member(999_999L, "الفيزياء", "", ""))), 400);
        call(post("/api/bundles"), admin, bundle("bad-photo", true, List.of(member(null, "الفيزياء", "javascript:alert(1)", ""))), 400);
        call(post("/api/bundles"), admin, bundle("excellence", true, List.of(member(null, "الفيزياء", "", ""))), 409);

        // A draft never reaches the public side.
        long draft = call(post("/api/bundles"), admin, bundle("draft-pack", false, List.of(member(physics, "الفيزياء", "", ""))), 200).path("id").asLong();
        call(get("/api/public/bundles/draft-pack"), null, null, 404);
        assertThat(call(get("/api/public/bundles"), null, null, 200).findValuesAsText("slug")).doesNotContain("draft-pack");

        // Unpublishing the teacher's own page removes it (and its videos/courses) from the package page.
        var content = new HashMap<String, Object>(Map.of("name", "مستر الفيزياء", "tagline", "الفيزياء", "headline", "عنوان",
                "description", "وصف", "aboutText", "نبذة", "subject", "الفيزياء", "phone", "", "demoContent", false, "published", false));
        call(put("/api/academies/" + physics), teacher, content, 200);
        assertThat(call(get("/api/public/bundles/excellence"), null, null, 200).path("members").get(2).hasNonNull("teacher")).isFalse();

        call(delete("/api/bundles/" + draft), admin, null, 200);
        assertThat(call(get("/api/bundles"), admin, null, 200).findValuesAsText("slug")).doesNotContain("draft-pack");
        String audit = mvc.perform(get("/api/dashboard/audit").header("Authorization", "Bearer " + admin)).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("BUNDLE_CHANGED", "BUNDLE_DELETED");
    }
}
