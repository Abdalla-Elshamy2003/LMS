package com.manarah.billing.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Fawaterak client against a stub server answering with the shapes from Fawaterak's API documentation. */
class FawaterakGatewayTest {
    final ObjectMapper json = new ObjectMapper();
    final Map<String, String> seen = new ConcurrentHashMap<>();
    volatile String initAnswer;
    HttpServer server;
    FawaterakGateway gateway;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        answer("/api/v2/getPaymentmethods", () -> """
                {"status":"success","data":[{"paymentId":2,"name_en":"Visa-Mastercard","name_ar":"فيزا"},
                                            {"paymentId":3,"name_en":"Fawry","name_ar":"فوري"}]}""");
        answer("/api/v2/invoiceInitPay", () -> initAnswer);
        answer("/api/v2/getInvoiceData/1000460", () -> """
                {"status":"success","data":{"invoice_id":1000460,"paid":1,"total":"110.00"}}""");
        server.start();
        gateway = new FawaterakGateway("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2/", "api-key", "vendor-key", json);
    }

    @AfterEach void stop() { server.stop(0); }

    void answer(String path, java.util.function.Supplier<String> body) {
        server.createContext(path, ex -> {
            seen.put(path + ":auth", String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            seen.put(path + ":body", new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.get().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
    }

    @Test void issuesAFawryCodeAndReadsItsStatus() throws Exception {
        initAnswer = """
                {"status":"success","data":{"invoice_id":1000460,"invoice_key":"eUeWTe1f6yiOOrt",
                 "payment_data":{"fawryCode":"9683888996","expireDate":"2026-10-09 15:53:15"}}}""";
        var code = gateway.createFawryCode("DR-ABC1234", new BigDecimal("110"),
                new FawryGateway.Customer("Ahmed", "Student7", "a@example.com", "01012345678"),
                List.of(new FawryGateway.Item("اشتراك", new BigDecimal("100")), new FawryGateway.Item("رسوم الدفع", new BigDecimal("10"))),
                "https://droos.example/api/public/payments/fawaterak/webhook_json", "https://droos.example/app/my-payments/5");

        assertThat(code.code()).isEqualTo("9683888996");
        assertThat(code.gatewayInvoiceId()).isEqualTo("1000460");
        assertThat(code.gatewayInvoiceKey()).isEqualTo("eUeWTe1f6yiOOrt");
        // Fawaterak gives the expiry in Cairo time without a zone.
        assertThat(code.expiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 9, 15, 53, 15).atZone(ZoneId.of("Africa/Cairo")).toInstant());

        // What was sent: Fawry's id from the list, the totals as text with piastres, and where to call back.
        assertThat(seen.get("/api/v2/invoiceInitPay:auth")).isEqualTo("Bearer api-key");
        JsonNode sent = json.readTree(seen.get("/api/v2/invoiceInitPay:body"));
        assertThat(sent.path("payment_method_id").asInt()).isEqualTo(3);
        assertThat(sent.path("cartTotal").asText()).isEqualTo("110.00");
        assertThat(sent.path("currency").asText()).isEqualTo("EGP");
        assertThat(sent.path("invoice_number").asText()).isEqualTo("DR-ABC1234");
        assertThat(sent.path("cartItems").findValuesAsText("price")).containsExactly("100.00", "10.00");
        assertThat(sent.path("customer").path("phone").asText()).isEqualTo("01012345678");
        assertThat(sent.path("redirectionUrls").path("webhookUrl").asText()).endsWith("/fawaterak/webhook_json");
        assertThat(sent.path("redirectionUrls").path("successUrl").asText()).endsWith("/app/my-payments/5");

        assertThat(gateway.isPaid("1000460")).contains(true);
    }

    @Test void aRefusalIsAClearErrorNotACode() {
        initAnswer = """
                {"status":"error","message":"Invalid payment method"}""";
        assertThatThrownBy(() -> gateway.createFawryCode("DR-ABC1235", BigDecimal.TEN,
                new FawryGateway.Customer("A", "B", "a@example.com", "01000000000"), List.of(new FawryGateway.Item("x", BigDecimal.TEN)),
                "https://droos.example/hook", "https://droos.example/back"))
                .isInstanceOf(BadRequestException.class);
    }
}
