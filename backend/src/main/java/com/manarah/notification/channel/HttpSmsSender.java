package com.manarah.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real SMS delivery over a provider's HTTP API, replacing the previous log-only stub.
 *
 * <p><b>Why this is configurable rather than hardcoded to one provider:</b> Egyptian SMS gateways
 * (Victory Link, SMSMisr, Connekio, Vodafone Bulk, ...) all speak plain HTTP with a handful of
 * query or form parameters, but every one of them names those parameters differently. Rather than
 * guess one vendor's spelling — and ship code that compiles and silently never sends — the
 * parameter names are configuration. Point it at whatever account the academy actually buys.
 *
 * <p>Example, a GET-style gateway:
 * <pre>
 *   manarah.sms.enabled=true
 *   manarah.sms.url=https://smsmisr.com/api/SMS/
 *   manarah.sms.method=GET
 *   manarah.sms.recipient-param=Mobile
 *   manarah.sms.message-param=Message
 *   manarah.sms.sender-id=MANARAH
 *   manarah.sms.sender-param=SenderID
 *   manarah.sms.extra-params=Username:myuser,Password:mypass,Language:2
 * </pre>
 * A JSON POST gateway instead: {@code method=POST}, plus
 * {@code auth-header=Bearer <token>} — the same parameter names are then sent as a JSON body.
 *
 * <p>Two things the academy must obtain from the provider, neither of which can be created from
 * code: a paid bulk-SMS account, and a registered <i>Sender ID</i> (the name that appears as the
 * sender). Arabic messages are usually billed as unicode — 70 characters per part, not 160.
 *
 * <p>Until configured this behaves exactly like the old stub: logs the intent and reports the
 * message as not dispatched, so the notification lands as "PENDING" rather than falsely "SENT".
 */
@Component
public class HttpSmsSender implements ExternalMessageSender {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsSender.class);

    private final boolean enabled;
    private final String url;
    private final String method;
    private final String recipientParam;
    private final String messageParam;
    private final String senderParam;
    private final String senderId;
    private final String authHeader;
    private final String extraParams;
    private final String defaultCountryCode;
    private final RestClient restClient = RestClient.create();

    public HttpSmsSender(
            @Value("${manarah.sms.enabled:false}") boolean enabled,
            @Value("${manarah.sms.url:}") String url,
            @Value("${manarah.sms.method:GET}") String method,
            @Value("${manarah.sms.recipient-param:mobile}") String recipientParam,
            @Value("${manarah.sms.message-param:message}") String messageParam,
            @Value("${manarah.sms.sender-param:sender}") String senderParam,
            @Value("${manarah.sms.sender-id:}") String senderId,
            @Value("${manarah.sms.auth-header:}") String authHeader,
            @Value("${manarah.sms.extra-params:}") String extraParams,
            @Value("${manarah.sms.default-country-code:20}") String defaultCountryCode) {
        this.enabled = enabled;
        this.url = url == null ? "" : url.trim();
        this.method = method == null ? "GET" : method.trim().toUpperCase(java.util.Locale.ROOT);
        this.recipientParam = recipientParam;
        this.messageParam = messageParam;
        this.senderParam = senderParam;
        this.senderId = senderId == null ? "" : senderId.trim();
        this.authHeader = authHeader == null ? "" : authHeader.trim();
        this.extraParams = extraParams == null ? "" : extraParams.trim();
        this.defaultCountryCode = defaultCountryCode;
    }

    @Override
    public String channel() {
        return "SMS";
    }

    @Override
    public boolean send(String recipient, String title, String body) {
        if (!enabled || url.isEmpty()) {
            log.info("[SMS STUB] outbound message skipped because the channel is not configured");
            return false;
        }
        String to = toE164(recipient);
        if (to == null) {
            log.warn("[SMS] skipped — recipient has no valid phone number");
            return false;
        }
        String text = (title != null && !title.isBlank()) ? title + "\n" + safe(body) : safe(body);

        Map<String, String> params = new LinkedHashMap<>();
        params.put(recipientParam, to);
        params.put(messageParam, text);
        if (!senderId.isEmpty()) {
            params.put(senderParam, senderId);
        }
        for (String pair : extraParams.split(",")) {
            int sep = pair.indexOf(':');
            if (sep > 0) {
                params.put(pair.substring(0, sep).trim(), pair.substring(sep + 1).trim());
            }
        }

        try {
            if ("POST".equals(method)) {
                restClient.post().uri(url)
                        .headers(h -> {
                            h.setContentType(MediaType.APPLICATION_JSON);
                            applyAuth(h);
                        })
                        .body(params)
                        .retrieve().toBodilessEntity();
            } else {
                var uri = UriComponentsBuilder.fromUriString(url);
                params.forEach(uri::queryParam);
                restClient.get().uri(uri.build(true).toUri())
                        .headers(this::applyAuth)
                        .retrieve().toBodilessEntity();
            }
            log.info("[SMS] gateway accepted delivery for recipient ending in {}", maskedSuffix(to));
            return true;
        } catch (Exception e) {
            log.error("[SMS] delivery failed for recipient ending in {}: {}", maskedSuffix(to), e.getClass().getSimpleName());
            return false;
        }
    }

    private void applyAuth(org.springframework.http.HttpHeaders h) {
        if (!authHeader.isEmpty()) {
            h.set("Authorization", authHeader);
        }
    }

    /** Normalises Egyptian-style local numbers (01xxxxxxxxx) to E.164 digits, as gateways expect. */
    private String toE164(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith("00")) return digits.substring(2);
        if (digits.startsWith("0")) return defaultCountryCode + digits.substring(1);
        if (digits.startsWith(defaultCountryCode)) return digits;
        return digits;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static String maskedSuffix(String raw) {
        return raw == null || raw.length() < 4 ? "****" : "****" + raw.substring(raw.length() - 4);
    }
}
