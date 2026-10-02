package com.manarah.billing.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fawry reference codes through Fawaterak (fawaterk.com), over its v2 API:
 * <ul>
 *   <li>{@code GET  getPaymentmethods} — finds Fawry's method id (looked up, not hardcoded);</li>
 *   <li>{@code POST invoiceInitPay} — opens an invoice and returns {@code payment_data.fawryCode} and {@code expireDate}
 *       (Cairo time, no zone in the text);</li>
 *   <li>{@code GET  getInvoiceData/{id}} — {@code data.paid == 1} once paid;</li>
 *   <li>webhooks, signed with HMAC-SHA256 under the vendor key.</li>
 * </ul>
 * Configured by {@code MANARAH_FAWATERAK_API_KEY}, {@code MANARAH_FAWATERAK_VENDOR_KEY} and
 * {@code MANARAH_FAWATERAK_BASE_URL} (https://staging.fawaterk.com/api/v2 for testing; the live API by default).
 */
@Component
public class FawaterakGateway implements FawryGateway {
    private static final Logger log = LoggerFactory.getLogger(FawaterakGateway.class);
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String baseUrl, apiKey, vendorKey;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile Integer fawryMethodId;

    public FawaterakGateway(@Value("${MANARAH_FAWATERAK_BASE_URL:https://app.fawaterk.com/api/v2}") String baseUrl,
                            @Value("${MANARAH_FAWATERAK_API_KEY:}") String apiKey,
                            @Value("${MANARAH_FAWATERAK_VENDOR_KEY:}") String vendorKey, ObjectMapper json) {
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.vendorKey = vendorKey == null ? "" : vendorKey.trim();
        this.json = json;
    }

    @Override
    public boolean configured() {
        return !apiKey.isEmpty() && !vendorKey.isEmpty();
    }

    @Override
    public FawryCode createFawryCode(String invoiceNumber, BigDecimal total, Customer customer, List<Item> items, String webhookUrl, String returnUrl) {
        if (!configured()) throw new BadRequestException("الدفع بفوري مش متاح دلوقتي");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_method_id", fawryMethodId());
        body.put("cartTotal", money(total));
        body.put("currency", "EGP");
        body.put("invoice_number", invoiceNumber);
        body.put("customer", Map.of("first_name", customer.firstName(), "last_name", customer.lastName(),
                "email", customer.email(), "phone", customer.phone(), "address", "Egypt"));
        body.put("redirectionUrls", Map.of("successUrl", returnUrl, "failUrl", returnUrl, "pendingUrl", returnUrl, "webhookUrl", webhookUrl));
        List<Map<String, Object>> cart = new ArrayList<>();
        for (Item i : items) cart.add(Map.of("name", i.name(), "price", money(i.price()), "quantity", "1"));
        body.put("cartItems", cart);

        JsonNode response = call("POST", "/invoiceInitPay", body);
        JsonNode data = response.path("data"), payment = data.path("payment_data");
        String code = payment.path("fawryCode").asText("");
        if (!"success".equalsIgnoreCase(response.path("status").asText()) || code.isBlank()) {
            log.warn("[fawaterak] invoiceInitPay refused for {}: {}", invoiceNumber, response);
            throw new BadRequestException("فوري ما طلّعش كود دلوقتي — جرّب كمان شوية أو ادفع بطريقة تانية");
        }
        Instant expires = null;
        String expireText = payment.path("expireDate").asText("");
        if (!expireText.isBlank()) {
            try { expires = LocalDateTime.parse(expireText.trim(), EXPIRY).atZone(CAIRO).toInstant(); }
            catch (Exception e) { log.warn("[fawaterak] unreadable expireDate '{}'", expireText); }
        }
        return new FawryCode(data.path("invoice_id").asText(), data.path("invoice_key").asText(), code, expires);
    }

    @Override
    public Optional<Boolean> isPaid(String gatewayInvoiceId) {
        if (!configured() || gatewayInvoiceId == null || gatewayInvoiceId.isBlank()) return Optional.empty();
        try {
            JsonNode data = call("GET", "/getInvoiceData/" + gatewayInvoiceId.replaceAll("[^0-9A-Za-z]", ""), null).path("data");
            if (data.isMissingNode() || data.isNull()) return Optional.empty();
            return Optional.of(data.path("paid").asInt(0) == 1);
        } catch (Exception e) {
            log.warn("[fawaterak] status check for {} failed: {}", gatewayInvoiceId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean validPaidSignature(String invoiceId, String invoiceKey, String paymentMethod, String hashKey) {
        return matches("InvoiceId=" + invoiceId + "&InvoiceKey=" + invoiceKey + "&PaymentMethod=" + paymentMethod, hashKey);
    }

    @Override
    public boolean validCancelSignature(String referenceId, String paymentMethod, String hashKey) {
        return matches("referenceId=" + referenceId + "&PaymentMethod=" + paymentMethod, hashKey);
    }

    private boolean matches(String message, String hashKey) {
        if (vendorKey.isEmpty() || hashKey == null || hashKey.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(vendorKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    hashKey.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    /** Fawry's method id at this gateway, from its list of methods (3 at the time of writing). */
    private int fawryMethodId() {
        Integer id = fawryMethodId;
        if (id != null) return id;
        try {
            for (JsonNode m : call("GET", "/getPaymentmethods", null).path("data"))
                if (m.path("name_en").asText("").toLowerCase(Locale.ROOT).contains("fawry")) { id = m.path("paymentId").asInt(); break; }
        } catch (Exception e) {
            log.warn("[fawaterak] could not list payment methods: {}", e.getMessage());
        }
        if (id == null || id <= 0) id = 3;
        fawryMethodId = id;
        return id;
    }

    private JsonNode call(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json").header("Accept", "application/json");
            if ("POST".equals(method)) request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
            else request.GET();
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("[fawaterak] {} {} answered {}: {}", method, path, response.statusCode(), response.body());
                throw new BadRequestException("بوابة الدفع مش بترد دلوقتي — جرّب كمان شوية");
            }
            return json.readTree(response.body());
        } catch (BadRequestException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("اتقطع الاتصال ببوابة الدفع");
        } catch (Exception e) {
            log.warn("[fawaterak] {} {} failed: {}", method, path, e.toString());
            throw new BadRequestException("بوابة الدفع مش بترد دلوقتي — جرّب كمان شوية");
        }
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
