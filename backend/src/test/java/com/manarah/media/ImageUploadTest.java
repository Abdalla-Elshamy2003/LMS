package com.manarah.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123"
})
@AutoConfigureMockMvc
class ImageUploadTest {
    private static final String RUN = "image-test-" + UUID.randomUUID();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        com.manarah.TestDatabase.register(props, RUN);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=");

    private String login(String email) throws Exception {
        var body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "manarah123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    @Test
    void teacherUploadsACoverThatAnyoneCanView() throws Exception {
        var res = mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "cover.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + login("teacher@manarah.io")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = json.readTree(res).get("url").asText();
        assertThat(url).matches("/api/public/images/\\d+");

        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(content().bytes(PNG));
    }

    @Test
    void notAnImageIsRejectedEvenWithAnImageName() throws Exception {
        mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "fake.png", "image/png", "<script>alert(1)</script>".getBytes()))
                        .header("Authorization", "Bearer " + login("teacher@manarah.io")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentsAndAnonymousVisitorsCannotUpload() throws Exception {
        var file = new MockMultipartFile("file", "cover.png", "image/png", PNG);
        mvc.perform(multipart("/api/images").file(file).header("Authorization", "Bearer " + login("student@manarah.io")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/images").file(file)).andExpect(status().isUnauthorized());
    }

    @Test
    void courseCoverMustBeAnUploadedImageOrHttpsLink() throws Exception {
        String teacher = login("teacher@manarah.io");
        String url = json.readTree(mvc.perform(multipart("/api/images").file(new MockMultipartFile("file", "c.png", "image/png", PNG))
                .header("Authorization", "Bearer " + teacher)).andReturn().getResponse().getContentAsString()).get("url").asText();
        mvc.perform(post("/api/courses").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "كورس بصورة", "coverUrl", url))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.coverUrl").value(url));
        mvc.perform(post("/api/courses").header("Authorization", "Bearer " + teacher).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "كورس خطر", "coverUrl", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest());
    }
}
