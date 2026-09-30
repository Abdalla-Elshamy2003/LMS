package com.manarah.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A course's units and their lessons, in the order the teacher sets. */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
}) @AutoConfigureMockMvc
class CourseStructureTest {
    static final String RUN = "course-structure-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) { com.manarah.TestDatabase.register(p, RUN); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    JsonNode ok(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String login(String user, String pass) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", user, "password", pass)))).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    List<String> titles(JsonNode nodes) { List<String> out = new ArrayList<>(); nodes.forEach(n -> out.add(n.path("title").asText())); return out; }

    @Test void teachersOrderUnitsAndLessons() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        for (String slug : List.of("order-a", "order-b"))
            ok(post("/api/admin/teachers"), admin, Map.of("name", "مستر " + slug, "subject", "الرياضيات", "slug", slug,
                    "username", slug.replace('-', '.'), "password", "TestPass123!", "courses", List.of(Map.of("title", "رياضة تالتة ثانوي"))));
        String teacher = login("order.a", "TestPass123!"), other = login("order.b", "TestPass123!");
        long course = ok(get("/api/courses"), teacher, null).path(0).path("id").asLong();
        long algebra = ok(post("/api/courses/" + course + "/modules"), teacher, Map.of("title", "الجبر")).path("id").asLong();
        long geometry = ok(post("/api/courses/" + course + "/modules"), teacher, Map.of("title", "الهندسة")).path("id").asLong();
        long eq = ok(post("/api/courses/modules/" + algebra + "/lessons"), teacher, Map.of("title", "المعادلات")).path("id").asLong();
        long ineq = ok(post("/api/courses/modules/" + algebra + "/lessons"), teacher, Map.of("title", "المتباينات")).path("id").asLong();

        ok(put("/api/courses/" + course + "/modules/order"), teacher, Map.of("ids", List.of(geometry, algebra)));
        ok(put("/api/courses/modules/" + algebra + "/lessons/order"), teacher, Map.of("ids", List.of(ineq, eq)));
        var detail = ok(get("/api/courses/" + course), teacher, null);
        assertThat(titles(detail.path("modules"))).containsExactly("الهندسة", "الجبر");
        assertThat(titles(detail.path("modules").path(1).path("lessons"))).containsExactly("المتباينات", "المعادلات");

        // The order must name every unit exactly once, and only the course's own teacher may set it.
        mvc.perform(put("/api/courses/" + course + "/modules/order").header("Authorization", "Bearer " + teacher)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids", List.of(geometry)))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/courses/" + course + "/modules/order").header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids", List.of(algebra, geometry)))))
                .andExpect(status().isNotFound());
    }
}
