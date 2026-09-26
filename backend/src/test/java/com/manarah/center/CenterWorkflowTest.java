package com.manarah.center;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.admin.AdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A tutoring center end to end: the head office opens it; the center adds a teacher, a group and its students; a
 * card's QR (or its code) marks the student present once per day and collects the fee; the attendance sheet shows who
 * was absent; the accounts split what was collected; a student reserves a book from their card's page and collects it
 * against the code. Another center can reach none of it. Plus the platform's admins, kept in the database.
 */
@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123",
        "manarah.public-app-url=https://droos.example/",
        "manarah.security.cors.allowed-origins=https://old-host.example"
}) @AutoConfigureMockMvc
class CenterWorkflowTest {
    static final String RUN = "center-test-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        com.manarah.TestDatabase.register(p, RUN);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminBootstrap bootstrap;
    @Autowired PasswordEncoder encoder;

    JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expected) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.header("X-Forwarded-For", "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out = mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return out.isBlank() ? json.nullNode() : json.readTree(out);
    }
    String login(String who, String password) throws Exception {
        return call(post("/api/auth/login"), null, Map.of("email", who, "password", password), 200).path("accessToken").asText();
    }

    @Test void centerRunsTeachersGroupsCardsAttendanceAccountsAndBooks() throws Exception {
        String admin = login("admin@manarah.io", "manarah123");
        JsonNode created = call(post("/api/admin/centers"), admin, Map.of("name", "سنتر النور", "username", "Nour.Center",
                "password", "CenterPass123!", "phone", "01000000000", "address", "المنصورة"), 200);
        assertThat(created.path("username").asText()).isEqualTo("nour.center");
        assertThat(created.path("active").asBoolean()).isTrue();
        call(post("/api/admin/centers"), admin, Map.of("name", "تاني", "username", "nour.center", "password", "CenterPass123!"), 409);
        call(post("/api/admin/centers"), admin, Map.of("name", "ضعيف", "username", "weak.center", "password", "short"), 400);

        String center = login("Nour.Center", "CenterPass123!");
        JsonNode me = call(get("/api/auth/me"), center, null, 200);
        assertThat(me.path("role").asText()).isEqualTo("CENTER_ADMIN");
        // A center is neither admin nor staff: none of the platform's own screens open for it, and vice versa.
        call(get("/api/students"), center, null, 403);
        call(get("/api/admin/centers"), center, null, 403);
        call(get("/api/center/overview"), admin, null, 403);

        long teacherId = call(post("/api/center/teachers"), center, Map.of("name", "مستر أحمد", "subject", "الفيزياء",
                "centerPercent", 20), 200).path("id").asLong();
        int today = LocalDate.now(CenterDays.ZONE).getDayOfWeek().getValue() % 7;
        int other = (today + 3) % 7;
        JsonNode group = call(post("/api/center/groups"), center, Map.of("teacherId", teacherId, "grade", "الصف الثالث الثانوي",
                "days", List.of(today, other), "startTime", "16:00", "endTime", "18:00", "room", "قاعة 2", "sessionPrice", 60), 200);
        long groupId = group.path("id").asLong();
        assertThat(group.path("subject").asText()).isEqualTo("الفيزياء");
        assertThat(group.path("timeLabel").asText()).isEqualTo("4:00 م – 6:00 م");
        call(post("/api/center/groups"), center, Map.of("teacherId", teacherId, "days", List.of(), "startTime", "16:00"), 400);

        JsonNode added = call(post("/api/center/students"), center, Map.of("groupId", groupId, "students", List.of(
                Map.of("name", "علي محمود", "phone", "01111111111"), Map.of("name", "منى سامي"), Map.of("name", "يوسف خالد"),
                Map.of("name", " "))), 200);
        assertThat(added.size()).isEqualTo(3);
        assertThat(added.get(0).path("code").asText()).isEqualTo("1001");
        assertThat(added.get(2).path("code").asText()).isEqualTo("1003");
        assertThat(added.get(0).path("teacherName").asText()).isEqualTo("مستر أحمد");
        String aliToken = added.get(0).path("token").asText(), monaCode = added.get(1).path("code").asText();
        long aliId = added.get(0).path("id").asLong(), yousefId = added.get(2).path("id").asLong();
        assertThat(aliToken).matches("[0-9a-f]{32}");

        // The QR carries the card's link; the first scan marks Ali present and collects the fee, the second changes nothing.
        JsonNode scan = call(post("/api/center/scan"), center, Map.of("code", "https://droos.com.co/c/" + aliToken), 200);
        assertThat(scan.path("outcome").asText()).isEqualTo("RECORDED");
        assertThat(scan.path("student").path("name").asText()).isEqualTo("علي محمود");
        assertThat(scan.path("amount").decimalValue()).isEqualByComparingTo("60");
        assertThat(scan.path("paid").asBoolean()).isTrue();
        assertThat(scan.path("warning").isMissingNode() || scan.path("warning").isNull()).isTrue();
        assertThat(call(post("/api/center/scan"), center, Map.of("code", aliToken), 200).path("outcome").asText()).isEqualTo("ALREADY");
        // Mona forgot her card: the code under the QR works too. Her fee stays open.
        long monaMark = call(post("/api/center/scan"), center, Map.of("code", monaCode), 200).path("attendanceId").asLong();
        call(put("/api/center/attendance/" + monaMark + "/paid"), center, Map.of("paid", false), 200);
        call(post("/api/center/scan"), center, Map.of("code", "9999"), 404);

        JsonNode sheet = call(get("/api/center/attendance?groupId=" + groupId), center, null, 200);
        assertThat(sheet.path("present").size()).isEqualTo(2);
        assertThat(sheet.path("absent").size()).isEqualTo(1);
        assertThat(sheet.path("absent").get(0).path("name").asText()).isEqualTo("يوسف خالد");
        assertThat(sheet.path("collected").decimalValue()).isEqualByComparingTo("60");
        assertThat(sheet.path("outstanding").decimalValue()).isEqualByComparingTo("60");
        assertThat(sheet.path("scheduled").asBoolean()).isTrue();

        // A price change later never rewrites today's session.
        call(put("/api/center/groups/" + groupId), center, Map.of("teacherId", teacherId, "days", List.of(today, other),
                "startTime", "16:00", "endTime", "18:00", "sessionPrice", 80), 200);
        JsonNode yousef = call(post("/api/center/attendance"), center, Map.of("studentId", yousefId), 200);
        assertThat(yousef.path("amount").decimalValue()).isEqualByComparingTo("60");

        JsonNode overview = call(get("/api/center/overview"), center, null, 200);
        assertThat(overview.path("presentToday").asLong()).isEqualTo(3);
        assertThat(overview.path("today").size()).isEqualTo(1);

        // Accounts: 180 due, 120 collected, the center's 20% of it, the rest for the teacher.
        JsonNode accounts = call(get("/api/center/accounts"), center, null, 200);
        JsonNode line = accounts.path("teachers").get(0);
        assertThat(line.path("sessions").asLong()).isEqualTo(1);
        assertThat(line.path("attendances").asLong()).isEqualTo(3);
        assertThat(line.path("due").decimalValue()).isEqualByComparingTo("180");
        assertThat(line.path("collected").decimalValue()).isEqualByComparingTo("120");
        assertThat(line.path("outstanding").decimalValue()).isEqualByComparingTo("60");
        assertThat(line.path("centerShare").decimalValue()).isEqualByComparingTo("24");
        assertThat(line.path("teacherShare").decimalValue()).isEqualByComparingTo("96");
        assertThat(accounts.path("unpaid").size()).isEqualTo(1);
        assertThat(accounts.path("unpaid").get(0).path("studentName").asText()).isEqualTo("منى سامي");

        // Books: the teacher's notes come out next week, two copies. Ali reserves from his card's page.
        long bookId = call(post("/api/center/books"), center, Map.of("teacherId", teacherId, "title", "ملزمة الكهربية",
                "price", 50, "releaseDate", LocalDate.now(CenterDays.ZONE).plusDays(7).toString(), "stock", 2), 200).path("id").asLong();
        JsonNode pass = call(get("/api/public/center-pass/" + aliToken), null, null, 200);
        assertThat(pass.path("teacherName").asText()).isEqualTo("مستر أحمد");
        assertThat(pass.path("centerName").asText()).isEqualTo("سنتر النور");
        assertThat(pass.path("attended").asLong()).isEqualTo(1);
        assertThat(pass.toString()).doesNotContain("01111111111");
        assertThat(pass.path("books").get(0).path("released").asBoolean()).isFalse();
        String code = call(post("/api/public/center-pass/" + aliToken + "/books/" + bookId + "/reserve"), null, null, 200).path("code").asText();
        assertThat(code).startsWith("BK-");
        assertThat(call(post("/api/public/center-pass/" + aliToken + "/books/" + bookId + "/reserve"), null, null, 200)
                .path("code").asText()).isEqualTo(code);
        call(post("/api/center/books/" + bookId + "/reservations"), center, Map.of("studentCode", monaCode), 200);
        // Two copies, both taken.
        call(post("/api/center/books/" + bookId + "/reservations"), center, Map.of("studentId", yousefId), 409);
        call(get("/api/public/center-pass/" + "0".repeat(32)), null, null, 404);

        JsonNode delivered = call(post("/api/center/reservations/deliver"), center, Map.of("code", code.toLowerCase()), 200);
        assertThat(delivered.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.path("studentName").asText()).isEqualTo("علي محمود");
        call(post("/api/center/reservations/deliver"), center, Map.of("code", code), 409);
        assertThat(call(get("/api/center/accounts"), center, null, 200).path("totals").path("booksIncome").decimalValue())
                .isEqualByComparingTo("50");

        // A lost card: the new QR works, the old one stops.
        String newToken = call(post("/api/center/students/" + aliId + "/new-card"), center, null, 200).path("token").asText();
        call(get("/api/public/center-pass/" + aliToken), null, null, 404);
        assertThat(call(post("/api/center/scan"), center, Map.of("code", newToken), 200).path("outcome").asText()).isEqualTo("ALREADY");
        // History of what happened stays: a student with attendance can be stopped, not deleted.
        call(delete("/api/center/students/" + aliId), center, null, 409);
        call(put("/api/center/students/" + aliId), center, Map.of("active", false), 200);
        call(post("/api/center/scan"), center, Map.of("code", newToken), 409);

        // Another center reaches nothing of this one — not by QR, code, id, or book.
        call(post("/api/admin/centers"), admin, Map.of("name", "سنتر تاني", "username", "other.center", "password", "CenterPass123!"), 200);
        String otherCenter = login("other.center", "CenterPass123!");
        call(post("/api/center/scan"), otherCenter, Map.of("code", newToken), 404);
        call(post("/api/center/scan"), otherCenter, Map.of("code", monaCode), 404);
        call(get("/api/center/students/" + yousefId), otherCenter, null, 404);
        call(post("/api/center/books/" + bookId + "/reservations"), otherCenter, Map.of("studentId", yousefId), 404);
        call(post("/api/center/reservations/deliver"), otherCenter, Map.of("code", code), 404);
        assertThat(call(get("/api/center/students"), otherCenter, null, 200).size()).isZero();

        // Nobody signs up into a center's tenant from the public registration form.
        String slug = "center-nour-center";
        call(post("/api/public/register"), null, Map.of("fullName", "متسلل", "email", "intruder@example.com", "password", "Intruder123!",
                "tenantSlug", slug), 403);

        // Locking a center signs it out at once.
        long centerId = created.path("id").asLong();
        call(put("/api/admin/centers/" + centerId + "/active"), admin, Map.of("active", false), 200);
        call(get("/api/center/overview"), center, null, 401);
        call(post("/api/auth/login"), null, Map.of("email", "nour.center", "password", "CenterPass123!"), 401);
        call(put("/api/admin/centers/" + centerId + "/active"), admin, Map.of("active", true), 200);
        call(put("/api/admin/centers/" + centerId + "/credentials"), admin, Map.of("username", "nour.center", "password", "NewCenter123!"), 200);
        login("nour.center", "NewCenter123!");
    }

    /** Behind the proxy a browser POST from the site's own https address is "cross-origin" to the backend; it must pass. */
    @Test void theSitesOwnAddressMaySignInFromTheBrowser() throws Exception {
        String body = json.writeValueAsString(Map.of("email", "admin@manarah.io", "password", "manarah123"));
        mvc.perform(post("/api/auth/login").header("Origin", "https://droos.example").header("X-Forwarded-For", "10.7.0.1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header("Origin", "https://old-host.example").header("X-Forwarded-For", "10.7.0.2")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").header("Origin", "https://evil.example").header("X-Forwarded-For", "10.7.0.3")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertThat(com.manarah.security.SecurityConfig.originOf("https://Droos.com.co/app")).isEqualTo("https://droos.com.co");
        assertThat(com.manarah.security.SecurityConfig.originOf("")).isNull();
    }

    @Test void adminsLiveInTheDatabaseAndAddEachOther() throws Exception {
        // The owner's account from the bootstrap variables: username and password exactly as the owner chose them.
        assertThat(bootstrap.createIfMissing("Abdalla_Elshamy", encoder.encode("Abdalla_Elshamy"))).isTrue();
        String owner = login("Abdalla_Elshamy", "Abdalla_Elshamy");
        assertThat(call(get("/api/auth/me"), owner, null, 200).path("role").asText()).isEqualTo("SUPER_ADMIN");
        login("abdalla_elshamy", "Abdalla_Elshamy");

        JsonNode added = call(post("/api/admin/admins"), owner, Map.of("fullName", "أدمن تاني", "username", "second.admin",
                "password", "SecondAdmin123!"), 200);
        long secondId = added.path("id").asLong();
        assertThat(added.path("role").asText()).isEqualTo("SUPER_ADMIN");
        call(post("/api/admin/admins"), owner, Map.of("fullName", "ضعيف", "username", "weak.admin", "password", "weak"), 400);
        call(post("/api/admin/admins"), owner, Map.of("fullName", "مكرر", "username", "second.admin", "password", "SecondAdmin123!"), 409);
        String second = login("second.admin", "SecondAdmin123!");
        JsonNode list = call(get("/api/admin/admins"), second, null, 200);
        assertThat(list.findValuesAsText("username")).contains("abdalla_elshamy", "second.admin");
        call(get("/api/admin/overview"), second, null, 200);

        // Nobody locks themselves; a locked admin is signed out; a password reset works; a branch admin manages none of it.
        long ownerId = call(get("/api/auth/me"), owner, null, 200).path("id").asLong();
        call(put("/api/admin/admins/" + ownerId + "/active"), owner, Map.of("active", false), 400);
        call(put("/api/admin/admins/" + secondId + "/active"), owner, Map.of("active", false), 200);
        call(get("/api/admin/overview"), second, null, 401);
        call(put("/api/admin/admins/" + secondId + "/active"), owner, Map.of("active", true), 200);
        call(put("/api/admin/admins/" + secondId + "/password"), owner, Map.of("password", "Rotated12345!"), 200);
        login("second.admin", "Rotated12345!");
        call(get("/api/admin/admins"), login("branch@manarah.io", "manarah123"), null, 403);
        String center = login("admin@manarah.io", "manarah123");
        call(post("/api/admin/centers"), center, Map.of("name", "سنتر", "username", "admins.center", "password", "CenterPass123!"), 200);
        call(get("/api/admin/admins"), login("admins.center", "CenterPass123!"), null, 403);
    }
}
