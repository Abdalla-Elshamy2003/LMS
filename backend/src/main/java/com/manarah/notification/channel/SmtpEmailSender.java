package com.manarah.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real email delivery over SMTP, replacing the previous log-only stub. Follows the same shape as
 * {@link WhatsAppCloudApiSender}: one bean for the channel, live only when configured, and never
 * claiming a message was sent when it wasn't (the notification then lands as "PENDING").
 *
 * <p>To go live set these (env vars work too — Spring relaxed binding maps
 * {@code MANARAH_EMAIL_ENABLED}, {@code SPRING_MAIL_HOST}, ...):
 * <pre>
 *   manarah.email.enabled=true
 *   manarah.email.from=no-reply@your-domain.com
 *   spring.mail.host=smtp.gmail.com
 *   spring.mail.port=587
 *   spring.mail.username=&lt;smtp user&gt;
 *   spring.mail.password=&lt;smtp password / app password&gt;
 *   spring.mail.properties.mail.smtp.auth=true
 *   spring.mail.properties.mail.smtp.starttls.enable=true
 * </pre>
 *
 * <p>{@code JavaMailSender} is autoconfigured only when {@code spring.mail.host} is present, so it
 * is injected through an {@link ObjectProvider}: requiring the bean outright would fail the whole
 * application on startup on any deployment that hasn't configured SMTP yet.
 *
 * <p>Gmail note: a normal account password will not work — generate an App Password with 2FA on.
 */
@Component
public class SmtpEmailSender implements ExternalMessageSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailer;
    private final boolean enabled;
    private final String from;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mailer,
                           @Value("${manarah.email.enabled:false}") boolean enabled,
                           @Value("${manarah.email.from:}") String from) {
        this.mailer = mailer;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public boolean send(String recipient, String title, String body) {
        JavaMailSender sender = enabled ? mailer.getIfAvailable() : null;
        if (sender == null || from.isEmpty() || recipient == null || recipient.isBlank()) {
            log.info("[EMAIL STUB] would deliver to '{}' :: {}", recipient, title);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient.trim());
            message.setSubject(title);
            message.setText(body);
            sender.send(message);
            log.info("[EMAIL] delivered to '{}' :: {}", recipient, title);
            return true;
        } catch (Exception e) {
            // A bounce or a bad SMTP credential must not fail the caller's transaction — the
            // notification is simply recorded as not dispatched.
            log.warn("[EMAIL] delivery to '{}' failed: {}", recipient, e.toString());
            return false;
        }
    }
}
