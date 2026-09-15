package com.manarah.payment;

import com.manarah.payment.PaymentDtos.*;
import com.manarah.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BRANCH_ADMIN','ACCOUNTANT','ACADEMIC_MANAGER')")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/invoices")
    public List<InvoiceView> list() {
        return service.list();
    }

    @GetMapping("/invoices/student/{studentId}")
    public List<InvoiceView> byStudent(@PathVariable Long studentId) {
        return service.byStudent(studentId);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/invoices")
    public InvoiceDetail create(@Valid @RequestBody CreateInvoiceRequest req) {
        return service.createInvoice(req);
    }

    @PostMapping("/record")
    public InvoiceDetail record(@AuthenticationPrincipal UserPrincipal actor, @Valid @RequestBody RecordPaymentRequest req) {
        return service.recordPayment(actor, req);
    }

    @PostMapping("/run-reminders")
    public int runReminders() {
        return service.runReminders();
    }
}
