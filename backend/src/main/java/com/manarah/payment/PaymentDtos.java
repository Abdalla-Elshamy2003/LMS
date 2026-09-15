package com.manarah.payment;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class PaymentDtos {

    public record InstallmentInput(BigDecimal amount, LocalDate dueDate) {
    }

    public record InstallmentView(Long id, int seq, BigDecimal amount, LocalDate dueDate, String status, Instant paidAt) {
    }

    public record PaymentView(Long id, BigDecimal amount, String method, String reference, Instant paidAt) {
    }

    public record InvoiceView(Long id, Long studentId, String studentName, Long courseId, String title,
                              BigDecimal totalAmount, BigDecimal discount, BigDecimal paidAmount, BigDecimal remaining,
                              String status, LocalDate dueDate) {
    }

    public record InvoiceDetail(InvoiceView invoice, List<InstallmentView> installments, List<PaymentView> payments) {
    }

    public record CreateInvoiceRequest(@NotNull Long studentId, Long courseId, String title,
                                       @NotNull BigDecimal totalAmount, BigDecimal discount, LocalDate dueDate,
                                       List<InstallmentInput> installments) {
    }

    public record RecordPaymentRequest(@NotNull Long invoiceId, Long installmentId, @NotNull BigDecimal amount,
                                       String method, String reference) {
    }
}
