package com.manarah.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manarah.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** A student's invoices («مدفوعاتي»), and the payment gateway's webhook. See {@link InvoiceService}. */
@RestController
@RequestMapping("/api")
public class InvoiceController {
    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);
    private final InvoiceService service;
    private final ObjectMapper json;

    public InvoiceController(InvoiceService service, ObjectMapper json) { this.service = service; this.json = json; }

    public record CreateInvoice(Long planId, Integer months, String method) {}

    @GetMapping("/me/payment-options") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.PaymentOptions options(@AuthenticationPrincipal UserPrincipal actor) { return service.options(actor); }

    @GetMapping("/me/invoices") @PreAuthorize("hasRole('STUDENT')")
    public List<InvoiceService.InvoiceView> mine(@AuthenticationPrincipal UserPrincipal actor) { return service.mine(actor); }

    @PostMapping("/me/invoices") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.InvoiceView create(@AuthenticationPrincipal UserPrincipal actor, @RequestBody CreateInvoice body) {
        return service.create(actor, body.planId(), body.months(), body.method());
    }

    @GetMapping("/me/invoices/{id}") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.InvoiceView get(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.get(actor, id); }

    /** "I paid": asks the gateway about a Fawry invoice. */
    @PostMapping("/me/invoices/{id}/check") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.InvoiceView check(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.check(actor, id); }

    @PostMapping("/me/invoices/{id}/renew") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.InvoiceView renew(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.renewCode(actor, id); }

    @PostMapping("/me/invoices/{id}/cancel") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.InvoiceView cancel(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id) { return service.cancel(actor, id); }

    @PostMapping(value = "/me/invoices/{id}/receipt", consumes = "multipart/form-data") @PreAuthorize("hasRole('STUDENT')")
    public InvoiceService.InvoiceView receipt(@AuthenticationPrincipal UserPrincipal actor, @PathVariable Long id,
                                              @RequestParam(required = false) String reference, @RequestParam(required = false) String sender,
                                              @RequestParam(required = false) MultipartFile receipt) {
        return service.sendReceipt(actor, id, reference, sender, receipt);
    }

    /**
     * Fawaterak's webhook (its "_json" address sends JSON; form fields are accepted too). Public, so it changes nothing
     * unless the vendor-key signature checks out.
     */
    @PostMapping({"/public/payments/fawaterak/webhook_json", "/public/payments/fawaterak/webhook"})
    public ResponseEntity<Map<String, String>> webhook(@RequestBody(required = false) String body) {
        // Read once, whichever way it came: JSON, or form fields (key=value&...).
        Map<String, String> fields = new HashMap<>();
        String text = body == null ? "" : body.trim();
        try {
            if (text.startsWith("{")) {
                JsonNode node = json.readTree(text);
                node.fieldNames().forEachRemaining(k -> { JsonNode v = node.get(k); if (v.isValueNode()) fields.put(k, v.asText()); });
            } else if (!text.isEmpty()) {
                for (String pair : text.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    fields.put(java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "unreadable"));
        }
        InvoiceService.WebhookResult result = service.webhook(fields);
        log.info("[fawaterak] webhook {} for invoice {}", result, fields.getOrDefault("invoice_id", fields.getOrDefault("referenceId", "?")));
        return switch (result) {
            case BAD_SIGNATURE -> ResponseEntity.status(401).body(Map.of("status", "bad signature"));
            case UNKNOWN_INVOICE -> ResponseEntity.status(404).body(Map.of("status", "unknown invoice"));
            default -> ResponseEntity.ok(Map.of("status", result.name().toLowerCase(Locale.ROOT)));
        };
    }
}
