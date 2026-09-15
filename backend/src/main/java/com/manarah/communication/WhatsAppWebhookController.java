package com.manarah.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Meta WhatsApp Cloud API webhook — receives inbound customer messages so
 * {@link WhatsAppInboundService} can auto-route them into the support inbox and send an
 * acknowledgment. Both endpoints are unauthenticated on purpose (see SecurityConfig): Meta calls
 * them directly, with no JWT. The GET subscription handshake uses the configured verify token,
 * while every POST is authenticated with Meta's {@code X-Hub-Signature-256} HMAC over the exact
 * raw request body before any customer data is parsed or persisted.
 *
 * <p>This round trip cannot be exercised end-to-end from this sandboxed environment — Meta
 * requires a public HTTPS URL registered in Meta Business Manager, which this container doesn't
 * have. The handshake and message parsing below follow Meta's documented payload shape exactly
 * and can be verified locally by POSTing a payload shaped like Meta's (see the class-level example
 * in the project notes), but a live Meta-to-server call has not been (and cannot be) observed here.
 */
@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final String verifyToken;
    private final String appSecret;
    private final WhatsAppInboundService inbound;
    private final ObjectMapper mapper;

    public WhatsAppWebhookController(@Value("${manarah.whatsapp.verify-token:}") String verifyToken,
                                     @Value("${manarah.whatsapp.app-secret:}") String appSecret,
                                     WhatsAppInboundService inbound,
                                     ObjectMapper mapper) {
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
        this.inbound = inbound;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!verifyToken.isBlank() && "subscribe".equals(mode) && verifyToken.equals(token) && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawPayload) {
        if (appSecret.isBlank()) {
            log.error("[WHATSAPP WEBHOOK] app secret is not configured; inbound webhook rejected");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!hasValidSignature(rawPayload, signature)) {
            log.warn("[WHATSAPP WEBHOOK] rejected request with an invalid Meta signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JsonNode payload = mapper.readTree(rawPayload);
            for (JsonNode entry : payload.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode message : change.path("value").path("messages")) {
                        String from = message.path("from").asText(null);
                        String body = message.path("text").path("body").asText(null);
                        if (from != null && body != null) inbound.handleIncoming(from, body);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WHATSAPP WEBHOOK] failed to process an authenticated payload: {}", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private boolean hasValidSignature(byte[] rawPayload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawPayload));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            log.error("[WHATSAPP WEBHOOK] cannot verify Meta signature", e);
            return false;
        }
    }
}
