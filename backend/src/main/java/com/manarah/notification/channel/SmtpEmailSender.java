package com.manarah.notification.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.MessagingException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Real email delivery, over one of two transports, and never claiming a message was sent when it wasn't (the
 * notification then lands as "PENDING"):
 * <ul>
 *   <li><b>Brevo's HTTPS API</b> — used whenever {@code MANARAH_BREVO_API_KEY} is set. This is what production uses:
 *       Railway blocks outgoing SMTP ports below its Pro plan, and plain HTTPS is never blocked.</li>
 *   <li><b>SMTP</b> — {@code spring.mail.*}, for hosts that allow it.</li>
 * </ul>
 * Either way {@code manarah.email.enabled=true} and {@code manarah.email.from} (an address on a domain verified with
 * the provider, e.g. no-reply@droos.com.co) are required.
 *
 * <p>{@code JavaMailSender} is autoconfigured only when {@code spring.mail.host} is present, so it is injected
 * through an {@link ObjectProvider}: requiring it outright would fail startup wherever SMTP isn't configured.
 */
@Component
public class SmtpEmailSender implements ExternalMessageSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final URI BREVO = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final String SENDER_NAME = "دروس";

    private final ObjectProvider<JavaMailSender> mailer;
    private final boolean enabled;
    private final String from;
    private final String brevoKey;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mailer,
                           @Value("${manarah.email.enabled:false}") boolean enabled,
                           @Value("${manarah.email.from:}") String from,
                           @Value("${MANARAH_BREVO_API_KEY:}") String brevoKey,
                           ObjectMapper json) {
        this.mailer = mailer;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
        this.brevoKey = brevoKey == null ? "" : brevoKey.trim();
        this.json = json;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    /** True when a real message can be sent: the channel is on, a sender address is set, and a transport exists. */
    public boolean isDeliverable() {
        return enabled && !from.isEmpty() && (!brevoKey.isEmpty() || mailer.getIfAvailable() != null);
    }

    /** The address mail goes out from, or blank when none is set. */
    public String from() {
        return from;
    }

    /** Which way mail goes out: "Brevo API", "SMTP", or blank when neither is configured. */
    public String transport() {
        return !brevoKey.isEmpty() ? "Brevo API" : mailer.getIfAvailable() != null ? "SMTP" : "";
    }

    /**
     * A PNG for the HTML body. SMTP embeds it and the body refers to it as {@code cid:contentId}; Brevo's API can't
     * embed, so there the body points at {@code publicUrl} instead and the PNG also rides along as an attachment.
     */
    public record InlineImage(String contentId, byte[] png, String publicUrl) {
        public InlineImage(String contentId, byte[] png) { this(contentId, png, null); }
    }

    /**
     * Sends an HTML email (with a plain-text alternative) that shows one image. Same contract as {@link #send}:
     * returns false — never throws — when email isn't configured or delivery fails.
     */
    public boolean sendHtml(String recipient, String subject, String text, String html, InlineImage image) {
        if (!isDeliverable() || recipient == null || recipient.isBlank()) {
            log.info("[EMAIL STUB] would deliver HTML to '{}' :: {}", recipient, subject);
            return false;
        }
        String cleanSubject = subject.replaceAll("[\\r\\n]+", " ");
        if (!brevoKey.isEmpty()) {
            String body = html;
            List<Map<String, String>> attachments = List.of();
            if (image != null) {
                if (image.publicUrl() != null) body = body.replace("cid:" + image.contentId(), HtmlUtils.htmlEscape(image.publicUrl()));
                attachments = List.of(Map.of("name", image.contentId() + ".png", "content", Base64.getEncoder().encodeToString(image.png())));
            }
            return brevo(recipient, cleanSubject, text, body, attachments) == null;
        }
        try {
            JavaMailSender sender = mailer.getObject();
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient.trim());
            helper.setSubject(cleanSubject);
            helper.setText(text, html);
            if (image != null) helper.addInline(image.contentId(), new ByteArrayResource(image.png()), "image/png");
            sender.send(message);
            log.info("[EMAIL] delivered HTML to '{}' :: {}", recipient, subject);
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.warn("[EMAIL] HTML delivery to '{}' failed: {}", recipient, e.toString());
            return false;
        }
    }

    /**
     * Sends one test message and says why it failed, for head office's "send a test email" button — unlike
     * {@link #send}, which only reports success. Returns null when the provider accepted the message.
     */
    public String sendTest(String recipient) {
        if (!enabled) return "الإيميل مقفول: MANARAH_EMAIL_ENABLED مش true";
        if (from.isEmpty()) return "مفيش عنوان مُرسِل: MANARAH_EMAIL_FROM فاضي";
        String subject = "رسالة تجربة من دروس";
        String text = "لو الرسالة دي وصلتك، يبقى إيميلات المنصة شغالة: استعادة كلمة المرور، وكارت الطالب، والإشعارات.";
        if (!brevoKey.isEmpty()) return brevo(recipient, subject, text, html(text), List.of());
        JavaMailSender sender = mailer.getIfAvailable();
        if (sender == null) return "مفيش طريقة إرسال: حط MANARAH_BREVO_API_KEY (أو إعدادات SMTP)";
        try {
            sender.send(plain(recipient, subject, text));
            log.info("[EMAIL] test delivered to '{}'", recipient);
            return null;
        } catch (Exception e) {
            log.warn("[EMAIL] test delivery to '{}' failed: {}", recipient, e.toString());
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    @Override
    public boolean send(String recipient, String title, String body) {
        if (!isDeliverable() || recipient == null || recipient.isBlank()) {
            log.info("[EMAIL STUB] would deliver to '{}' :: {}", recipient, title);
            return false;
        }
        if (!brevoKey.isEmpty()) return brevo(recipient, title, body, html(body), List.of()) == null;
        try {
            mailer.getObject().send(plain(recipient, title, body));
            log.info("[EMAIL] delivered to '{}' :: {}", recipient, title);
            return true;
        } catch (Exception e) {
            // A bounce or a bad SMTP credential must not fail the caller's transaction — the
            // notification is simply recorded as not dispatched.
            log.warn("[EMAIL] delivery to '{}' failed: {}", recipient, e.toString());
            return false;
        }
    }

    private SimpleMailMessage plain(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient.trim());
        message.setSubject(subject);
        message.setText(body);
        return message;
    }

    /** A plain message as right-to-left HTML, since Brevo wants an HTML body. */
    private static String html(String text) {
        return "<div dir=\"rtl\" style=\"font-family:Tahoma,Arial,sans-serif;font-size:15px;line-height:1.9\">"
                + HtmlUtils.htmlEscape(text).replace("\n", "<br>") + "</div>";
    }

    /** Sends through Brevo's transactional email API. Returns null when accepted, else why not. */
    private String brevo(String recipient, String subject, String text, String html, List<Map<String, String>> attachments) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sender", Map.of("email", from, "name", SENDER_NAME));
            body.put("to", List.of(Map.of("email", recipient.trim())));
            body.put("subject", subject);
            body.put("htmlContent", html);
            if (text != null && !text.isBlank()) body.put("textContent", text);
            if (!attachments.isEmpty()) body.put("attachment", attachments);
            HttpRequest request = HttpRequest.newBuilder(BREVO).timeout(Duration.ofSeconds(20))
                    .header("api-key", brevoKey).header("accept", "application/json").header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 == 2) {
                log.info("[EMAIL] delivered via Brevo to '{}' :: {}", recipient, subject);
                return null;
            }
            String why = json.readTree(response.body()).path("message").asText(response.body());
            log.warn("[EMAIL] Brevo refused mail to '{}' ({}): {}", recipient, response.statusCode(), why);
            return "Brevo رفض الرسالة (" + response.statusCode() + "): " + why;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "اتقطع الإرسال";
        } catch (Exception e) {
            log.warn("[EMAIL] Brevo delivery to '{}' failed: {}", recipient, e.toString());
            return "تعذّر الوصول لـ Brevo: " + e.getMessage();
        }
    }
}
