package com.manarah.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.manarah.payment.gateway.PaymobGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives Paymob's "Transaction Processed Callback" (server-to-server, POST) once a course
 * payment settles. Public/unauthenticated on purpose (see SecurityConfig) — Paymob calls this
 * directly with no JWT; {@link PaymobGatewayService#verifyWebhookHmac} is the only trust check.
 *
 * <p>Like the WhatsApp webhook, this round trip cannot be exercised end-to-end from this
 * sandboxed environment (no public HTTPS URL registered with Paymob, and no live merchant
 * credentials) — the payload parsing and HMAC verification follow Paymob's documented shape
 * exactly and are testable with a simulated POST, but an actual Paymob-to-container call has not
 * been observed here.
 */
@RestController
@RequestMapping("/api/payments/paymob")
public class PaymobWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymobWebhookController.class);

    private final PaymobGatewayService gateway;
    private final CourseCheckoutService checkout;

    public PaymobWebhookController(PaymobGatewayService gateway, CourseCheckoutService checkout) {
        this.gateway = gateway;
        this.checkout = checkout;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestParam(required = false) String hmac, @RequestBody JsonNode payload) {
        try {
            JsonNode obj = payload.path("obj");
            String receivedHmac = (hmac != null && !hmac.isBlank()) ? hmac : payload.path("hmac").asText(null);
            if (!gateway.verifyWebhookHmac(obj, receivedHmac)) {
                log.warn("[PAYMOB WEBHOOK] HMAC verification failed or gateway not enabled — ignoring callback");
                return ResponseEntity.ok().build();
            }
            boolean success = obj.path("success").asBoolean(false);
            String merchantOrderId = obj.path("order").path("merchant_order_id").asText(null);
            String transactionId = obj.path("id").asText(null);
            if (success && merchantOrderId != null && !merchantOrderId.isBlank()) {
                checkout.confirmPaid(merchantOrderId, transactionId);
            }
        } catch (Exception e) {
            log.error("[PAYMOB WEBHOOK] failed to process callback: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
