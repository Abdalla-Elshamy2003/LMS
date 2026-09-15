package com.manarah.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Real course-payment gateway via Paymob Accept (the standard Egyptian aggregator for card and
 * mobile-wallet payments — Vodafone Cash, Orange Cash, e& money, ...). Built against Paymob's
 * current "Intention API" + "Unified Checkout" flow (docs verified live at developers.paymob.com,
 * June–August 2026 revisions):
 *   1. POST {api-base-url}/v1/intention/ (Authorization: Token &lt;secret key&gt;) → client_secret
 *   2. Redirect the customer to {checkout-base-url}?publicKey=...&amp;clientSecret=... — Paymob's
 *      own hosted page shows whichever payment methods the configured integration-ids enable.
 *   3. Paymob POSTs a "Transaction Processed Callback" to our webhook once the payment settles;
 *      {@link #verifyWebhookHmac} authenticates it before anything acts on it.
 *
 * <p><b>Fawry / InstaPay honesty note:</b> Paymob's currently-documented Egypt payment methods
 * are cards and mobile wallets (Vodafone Cash, Orange Cash, e&amp; money, We Pay) plus BNPL —
 * Fawry does not appear as a listed integration type in their current docs, and InstaPay
 * (the central bank's instant-payment network) is not exposed by Paymob at all as far as these
 * docs show. This service therefore makes no Fawry/InstaPay-specific promises: it only ever
 * shows the payment methods that actually exist behind the merchant's own configured
 * integration IDs — never a hardcoded assumption about which buttons Paymob will render.
 *
 * <p>Disabled by default (same pattern as {@code WhatsAppCloudApiSender}/{@code AiQuestionGenerator}):
 * until real Paymob credentials are configured, {@link #isEnabled()} is false and callers fall
 * back to the existing sandbox/demo checkout flow.
 */
@Component
public class PaymobGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymobGatewayService.class);

    /** Exact field order Paymob specifies for the Transaction Processed (POST) callback HMAC —
     *  verified against developers.paymob.com's "HMAC Transaction Callback" page. Paths are
     *  relative to the callback's "obj" node; dots navigate into nested objects. */
    private static final List<String> HMAC_FIELDS = List.of(
            "amount_cents", "created_at", "currency", "error_occured", "has_parent_transaction",
            "id", "integration_id", "is_3d_secure", "is_auth", "is_capture", "is_refunded",
            "is_standalone_payment", "is_voided", "order.id", "owner", "pending",
            "source_data.pan", "source_data.sub_type", "source_data.type", "success");

    private final boolean configuredEnabled;
    private final String secretKey;
    private final String publicKey;
    private final String hmacSecret;
    private final List<String> integrationIds;
    private final String apiBaseUrl;
    private final String checkoutBaseUrl;
    private final String publicAppUrl;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public PaymobGatewayService(
            @Value("${manarah.payment.paymob.enabled:false}") boolean enabled,
            @Value("${manarah.payment.paymob.secret-key:}") String secretKey,
            @Value("${manarah.payment.paymob.public-key:}") String publicKey,
            @Value("${manarah.payment.paymob.hmac-secret:}") String hmacSecret,
            @Value("${manarah.payment.paymob.integration-ids:}") String integrationIdsCsv,
            @Value("${manarah.payment.paymob.api-base-url:https://accept.paymob.com}") String apiBaseUrl,
            @Value("${manarah.payment.paymob.checkout-base-url:https://eg.checkout.paymob.com/}") String checkoutBaseUrl,
            @Value("${manarah.payment.paymob.public-app-url:}") String publicAppUrl) {
        this.configuredEnabled = enabled;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.hmacSecret = hmacSecret;
        this.integrationIds = integrationIdsCsv == null || integrationIdsCsv.isBlank() ? List.of()
                : Arrays.stream(integrationIdsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        this.apiBaseUrl = apiBaseUrl;
        this.checkoutBaseUrl = checkoutBaseUrl;
        this.publicAppUrl = publicAppUrl;
    }

    /** False whenever any required credential is missing — callers must fall back to the
     *  existing sandbox/demo flow rather than pretend to charge real money. */
    public boolean isEnabled() {
        return configuredEnabled && !secretKey.isBlank() && !publicKey.isBlank() && !hmacSecret.isBlank()
                && !integrationIds.isEmpty();
    }

    public record BillingData(String fullName, String email, String phone) {}

    public record CheckoutSession(String checkoutUrl, Long paymobOrderId) {}

    /** Creates a Paymob payment intention for one course purchase and returns the hosted
     *  checkout URL to redirect the customer to. Empty when the gateway isn't configured, or on
     *  any error talking to Paymob (logged, never thrown — checkout falls back to demo mode). */
    public Optional<CheckoutSession> createCheckout(String merchantOrderReference, BigDecimal amountEgp,
                                                     String courseTitle, BillingData billing) {
        if (!isEnabled()) return Optional.empty();
        try {
            long amountCents = amountEgp.movePointRight(2).longValueExact();
            String[] name = splitName(billing.fullName());
            Map<String, Object> billingData = new LinkedHashMap<>();
            billingData.put("first_name", name[0]);
            billingData.put("last_name", name[1]);
            billingData.put("email", billing.email() == null || billing.email().isBlank() ? "na@manarah.io" : billing.email());
            billingData.put("phone_number", billing.phone() == null || billing.phone().isBlank() ? "+20000000000" : billing.phone());
            for (String field : List.of("apartment", "floor", "street", "building", "city", "state"))
                billingData.put(field, "NA");
            billingData.put("country", "EG");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amountCents);
            body.put("currency", "EGP");
            body.put("payment_methods", integrationIds);
            body.put("items", List.of(Map.of("name", courseTitle, "amount", amountCents,
                    "description", courseTitle, "quantity", 1)));
            body.put("billing_data", billingData);
            body.put("special_reference", merchantOrderReference);
            body.put("expiration", 3600);
            if (publicAppUrl != null && !publicAppUrl.isBlank()) {
                body.put("notification_url", publicAppUrl + "/api/payments/paymob/webhook");
                body.put("redirection_url", publicAppUrl + "/app/payment/return?ref=" + merchantOrderReference);
            }

            String response = restClient.post()
                    .uri(apiBaseUrl + "/v1/intention/")
                    .headers(h -> {
                        h.set("Authorization", "Token " + secretKey);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(response == null ? "{}" : response);
            String clientSecret = node.path("client_secret").asText(null);
            if (clientSecret == null || clientSecret.isBlank()) {
                log.warn("[PAYMOB] intention response is missing client_secret");
                return Optional.empty();
            }
            long orderId = node.path("intention_order_id").asLong(0);
            String url = checkoutBaseUrl + "?publicKey=" + publicKey + "&clientSecret=" + clientSecret;
            return Optional.of(new CheckoutSession(url, orderId));
        } catch (Exception e) {
            log.error("[PAYMOB] failed to create checkout for order '{}': {}", merchantOrderReference, e.getMessage());
            return Optional.empty();
        }
    }

    /** Authenticates a Transaction Processed (POST) callback. {@code obj} is the callback's
     *  {@code obj} node; {@code receivedHmac} is the hmac Paymob sent (query param on the
     *  webhook URL, per their docs — checked as a fallback inside the JSON body too, in case a
     *  given account's callback shape differs from what's currently documented). */
    public boolean verifyWebhookHmac(JsonNode obj, String receivedHmac) {
        if (!isEnabled() || receivedHmac == null || receivedHmac.isBlank()) return false;
        String concatenated = HMAC_FIELDS.stream().map(path -> valueAt(obj, path)).reduce("", String::concat);
        String computed = hmacSha512Hex(hmacSecret, concatenated);
        return computed.equalsIgnoreCase(receivedHmac);
    }

    private static String valueAt(JsonNode root, String dottedPath) {
        JsonNode node = root;
        for (String part : dottedPath.split("\\.")) node = node.path(part);
        return node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static String hmacSha512Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA512 unavailable", e);
        }
    }

    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"NA", "NA"};
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[]{trimmed, "NA"} : new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)};
    }
}
