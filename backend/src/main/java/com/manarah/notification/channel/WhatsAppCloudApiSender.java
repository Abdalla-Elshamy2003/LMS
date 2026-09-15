package com.manarah.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Real WhatsApp delivery via the Meta WhatsApp Cloud API (Graph API "/messages" endpoint).
 *
 * <p>To go live, set these three properties (env vars work too — Spring relaxed binding maps
 * {@code MANARAH_WHATSAPP_ACCESS_TOKEN} etc.):
 * <pre>
 *   manarah.whatsapp.enabled=true
 *   manarah.whatsapp.access-token=&lt;a Meta permanent or temporary access token&gt;
 *   manarah.whatsapp.phone-number-id=&lt;the Cloud API "From" phone number id&gt;
 * </pre>
 * Until those are set, this sender behaves exactly like the previous stub: it logs the
 * intended message and reports it as not dispatched (the notification lands as "PENDING"
 * rather than falsely "SENT"). No code changes are needed to go live — only configuration.
 *
 * <p><b>Template requirement:</b> Meta requires an approved message <i>template</i> for the
 * first business-initiated message in a 24h window (or any message to a number that hasn't
 * messaged your business first). This sender sends a free-form text message, which Meta only
 * delivers within 24h of the customer's last inbound message, or from numbers still in
 * sandbox/test mode. To send truly proactive parent alerts at scale, register a template in
 * Meta Business Manager and swap the payload built in {@link #send} for a {@code template}
 * object referencing it — the rest of the plumbing (config, phone normalisation, error
 * handling) stays the same.
 */
@Component
public class WhatsAppCloudApiSender implements ExternalMessageSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiSender.class);

    private final boolean enabled;
    private final String accessToken;
    private final String phoneNumberId;
    private final String apiVersion;
    private final String defaultCountryCode;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public WhatsAppCloudApiSender(
            @Value("${manarah.whatsapp.enabled:false}") boolean enabled,
            @Value("${manarah.whatsapp.access-token:}") String accessToken,
            @Value("${manarah.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${manarah.whatsapp.api-version:v20.0}") String apiVersion,
            @Value("${manarah.whatsapp.default-country-code:20}") String defaultCountryCode) {
        this.enabled = enabled;
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
        this.apiVersion = apiVersion;
        this.defaultCountryCode = defaultCountryCode;
    }

    @Override
    public String channel() {
        return "WHATSAPP";
    }

    private boolean isConfigured() {
        return enabled && accessToken != null && !accessToken.isBlank()
                && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    @Override
    public boolean send(String recipient, String title, String body) {
        if (!isConfigured()) {
            log.info("[WHATSAPP STUB] outbound message skipped because the channel is not configured");
            return false;
        }
        String to = toE164(recipient);
        if (to == null) {
            log.warn("[WHATSAPP] skipped — recipient has no valid phone number");
            return false;
        }
        String text = (title != null && !title.isBlank()) ? title + "\n" + safe(body) : safe(body);
        try {
            String url = "https://graph.facebook.com/%s/%s/messages".formatted(apiVersion, phoneNumberId);
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "text",
                    "text", Map.of("preview_url", false, "body", text));

            String response = restClient.post()
                    .uri(url)
                    .headers(h -> {
                        h.setBearerAuth(accessToken);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(response == null ? "{}" : response);
            boolean ok = node.has("messages");
            if (!ok) {
                log.warn("[WHATSAPP] Meta API responded without a message id");
            }
            return ok;
        } catch (Exception e) {
            log.error("[WHATSAPP] delivery failed for recipient ending in {}: {}", maskedSuffix(to), e.getClass().getSimpleName());
            return false;
        }
    }

    /** Normalises Egyptian-style local numbers (01xxxxxxxxx) to E.164 digits (no '+'), Meta's expected format. */
    private String toE164(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith("00")) return digits.substring(2);
        if (digits.startsWith("0")) return defaultCountryCode + digits.substring(1);
        if (digits.length() > 11) return digits; // already looks international
        return defaultCountryCode + digits;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String maskedSuffix(String raw) {
        return raw == null || raw.length() < 4 ? "****" : "****" + raw.substring(raw.length() - 4);
    }
}
