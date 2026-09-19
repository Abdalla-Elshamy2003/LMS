package com.manarah.student.pass;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.manarah.identity.repo.UserRepository;
import com.manarah.org.repo.TenantRepository;
import com.manarah.student.repo.StudentRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "manarah.security.jwt.secret=dGVzdC1vbmx5LW1hbmFyYWgtand0LXNlY3JldC0zMi1ieXRlcy1taW4=",
        "manarah.demo.seed-enabled=true",
        "manarah.demo.password=manarah123",
        "manarah.email.enabled=true",
        "manarah.email.from=no-reply@manarah.test",
        "manarah.public-app-url=https://manarah.test/"
})
@AutoConfigureMockMvc
class StudentRegistrationEmailTest {
    private static final String RUN = "pass-email-test-" + UUID.randomUUID();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        com.manarah.TestDatabase.register(props, RUN);
        props.add("manarah.storage.root", () -> Path.of("target", RUN + "-files").toAbsolutePath().toString());
    }

    @MockitoBean JavaMailSender mailSender;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;

    private static final String PASSWORD = "Str0ngPassw0rd!";

    private MimeMessage newMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private void register(String name, String email) throws Exception {
        String slug = tenants.findAll().get(0).getSlug();
        mvc.perform(post("/api/public/register").contentType(MediaType.APPLICATION_JSON)
                        // A distinct client IP per call keeps the per-IP signup rate limit (shared Redis) out of the way.
                        .header("X-Forwarded-For", "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + ".7")
                        .content(json.writeValueAsString(Map.of("fullName", name, "email", email, "password", PASSWORD,
                                "phone", "01012345678", "grade", "الصف الثاني الثانوي", "nationalId", "29801011234567",
                                "tenantSlug", slug))))
                .andExpect(status().isOk());
    }

    private static void collect(Part part, List<Part> out) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) collect(multipart.getBodyPart(i), out);
        } else {
            out.add(part);
        }
    }

    private static String decodeQr(byte[] png) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));
        return new QRCodeReader().decode(bitmap).getText();
    }

    @Test
    void registrationEmailsTheStudentTheirQrAndDetailsWithoutSecrets() throws Exception {
        MimeMessage message = newMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);
        String email = "student-" + UUID.randomUUID() + "@example.com";

        register("<b>علي</b> محمود", email);

        verify(mailSender, timeout(10_000)).send(any(MimeMessage.class));
        message.saveChanges(); // what a real JavaMailSender does on send: resolves content types before we inspect the parts
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(message.getSubject()).contains("QR");

        var parts = new ArrayList<Part>();
        collect(message, parts);
        String html = null;
        byte[] png = null;
        for (Part part : parts) {
            if (part.isMimeType("text/html")) html = (String) part.getContent();
            if (part.isMimeType("image/png")) png = part.getInputStream().readAllBytes();
        }
        assertThat(html).isNotNull();
        assertThat(png).as("inline QR image").isNotNull();

        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        var student = students.findByTenantIdAndUserId(user.getTenantId(), user.getId()).orElseThrow();

        // The QR really encodes this student's public verification URL.
        assertThat(decodeQr(png)).isEqualTo("https://manarah.test/student/verify/" + student.getPassToken());

        // Their own details are in the mail...
        assertThat(html).contains(student.getCode(), "الصف الثاني الثانوي", email);
        // ...user-supplied text is escaped rather than injected as markup...
        assertThat(html).contains("&lt;b&gt;").doesNotContain("<b>علي");
        // ...and nothing secret or sensitive is.
        assertThat(html).doesNotContain(PASSWORD, "29801011234567", "01012345678");
    }

    @Test
    void aMailFailureNeverBreaksRegistration() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> newMessage());
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));
        String email = "student-" + UUID.randomUUID() + "@example.com";

        register("سارة أحمد", email);

        verify(mailSender, timeout(10_000)).send(any(MimeMessage.class));
        assertThat(users.findByEmailIgnoreCase(email)).isPresent();
    }
}
