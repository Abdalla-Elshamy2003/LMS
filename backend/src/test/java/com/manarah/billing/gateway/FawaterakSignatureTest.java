package com.manarah.billing.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Fawaterak webhooks are signed with HMAC-SHA256 under the vendor key, over two different strings. */
class FawaterakSignatureTest {
    static String hmac(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void checksBothSignatureShapes() throws Exception {
        var gateway = new FawaterakGateway("https://staging.fawaterk.com/api/v2", "api-key", "vendor-key", new ObjectMapper());
        String paid = hmac("vendor-key", "InvoiceId=1000425&InvoiceKey=QqgdnAB7Ad2kmIq&PaymentMethod=Fawry");
        assertThat(gateway.validPaidSignature("1000425", "QqgdnAB7Ad2kmIq", "Fawry", paid)).isTrue();
        assertThat(gateway.validPaidSignature("1000425", "QqgdnAB7Ad2kmIq", "Fawry", paid.toUpperCase())).isTrue();
        assertThat(gateway.validPaidSignature("1000426", "QqgdnAB7Ad2kmIq", "Fawry", paid)).isFalse();
        assertThat(gateway.validPaidSignature("1000425", "QqgdnAB7Ad2kmIq", "Fawry", "")).isFalse();
        String cancelled = hmac("vendor-key", "referenceId=ABC123&PaymentMethod=Fawry");
        assertThat(gateway.validCancelSignature("ABC123", "Fawry", cancelled)).isTrue();
        assertThat(gateway.validCancelSignature("ABC124", "Fawry", cancelled)).isFalse();
        // No vendor key on the server: nothing is ever accepted.
        var unconfigured = new FawaterakGateway("https://staging.fawaterk.com/api/v2", "", "", new ObjectMapper());
        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.validPaidSignature("1000425", "QqgdnAB7Ad2kmIq", "Fawry", paid)).isFalse();
    }
}
