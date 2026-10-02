package com.manarah.billing.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A payment gateway that issues Fawry reference codes: the student pays the code at any Fawry outlet, the gateway
 * tells us through a signed webhook. Production uses {@link FawaterakGateway}; tests swap in a fake.
 */
public interface FawryGateway {
    /** False until the gateway's keys are set on the server: Fawry isn't offered then. */
    boolean configured();

    record Customer(String firstName, String lastName, String email, String phone) {}
    record Item(String name, BigDecimal price) {}
    record FawryCode(String gatewayInvoiceId, String gatewayInvoiceKey, String code, Instant expiresAt) {}

    /** Opens a gateway invoice for {@code total} (the sum of {@code items}) and returns its Fawry reference code; the gateway
     *  reports payment to {@code webhookUrl} and sends any browser back to {@code returnUrl}. */
    FawryCode createFawryCode(String invoiceNumber, BigDecimal total, Customer customer, List<Item> items, String webhookUrl, String returnUrl);

    /** Whether the gateway has the invoice as paid; empty when it couldn't tell (a webhook still settles it). */
    Optional<Boolean> isPaid(String gatewayInvoiceId);

    /** A "paid" or "failed" webhook's signature over InvoiceId, InvoiceKey and PaymentMethod. */
    boolean validPaidSignature(String invoiceId, String invoiceKey, String paymentMethod, String hashKey);

    /** A "cancelled" or "expired" webhook's signature over referenceId and PaymentMethod. */
    boolean validCancelSignature(String referenceId, String paymentMethod, String hashKey);
}
