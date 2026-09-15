package com.manarah.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder external senders for channels without a live integration yet.
 * Each logs what it WOULD send and returns false (not dispatched), so the notification
 * is recorded with channel status "PENDING" rather than falsely "SENT".
 * WhatsApp, email and SMS have real adapters: {@link WhatsAppCloudApiSender},
 * {@link SmtpEmailSender}, {@link HttpSmsSender} — only PUSH is still a stub.
 */
public class StubSenders {

    private StubSenders() {
    }

    private static void log(Logger log, String channel, String to, String title) {
        log.info("[{} STUB] would deliver to '{}' :: {}", channel, to, title);
    }

    @Component
    public static class PushSender implements ExternalMessageSender {
        private static final Logger log = LoggerFactory.getLogger(PushSender.class);
        public String channel() { return "PUSH"; }
        public boolean send(String recipient, String title, String body) {
            log(log, "PUSH", recipient, title);
            return false;
        }
    }
}
