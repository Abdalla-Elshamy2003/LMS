package com.manarah.payment;

import com.manarah.common.events.DomainEvents;
import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.payment.PaymentDtos.*;
import com.manarah.payment.domain.Installment;
import com.manarah.payment.domain.Invoice;
import com.manarah.payment.domain.Payment;
import com.manarah.payment.repo.InstallmentRepository;
import com.manarah.payment.repo.InvoiceRepository;
import com.manarah.payment.repo.PaymentRepository;
import com.manarah.security.UserPrincipal;
import com.manarah.student.repo.StudentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentService {

    private final InvoiceRepository invoices;
    private final InstallmentRepository installments;
    private final PaymentRepository payments;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;

    public PaymentService(InvoiceRepository invoices, InstallmentRepository installments, PaymentRepository payments,
                          StudentRepository students, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.installments = installments;
        this.payments = payments;
        this.students = students;
        this.events = events;
    }

    public List<InvoiceView> list() {
        Long tenantId = TenantContext.require();
        return invoices.findByTenantId(tenantId).stream().map(this::toInvoiceView).toList();
    }

    public List<InvoiceView> byStudent(Long studentId) {
        Long tenantId = TenantContext.require();
        return invoices.findByTenantIdAndStudentId(tenantId, studentId).stream().map(this::toInvoiceView).toList();
    }

    public InvoiceDetail detail(Long invoiceId) {
        Long tenantId = TenantContext.require();
        Invoice inv = invoices.findByTenantIdAndId(tenantId, invoiceId).orElseThrow(() -> NotFoundException.of("الفاتورة", invoiceId));
        List<InstallmentView> insts = installments.findByTenantIdAndInvoiceIdOrderBySeq(tenantId, invoiceId).stream()
                .map(i -> new InstallmentView(i.getId(), i.getSeq(), i.getAmount(), i.getDueDate(), i.getStatus(), i.getPaidAt()))
                .toList();
        List<PaymentView> pays = payments.findByTenantIdAndInvoiceId(tenantId, invoiceId).stream()
                .map(p -> new PaymentView(p.getId(), p.getAmount(), p.getMethod(), p.getReference(), p.getPaidAt()))
                .toList();
        return new InvoiceDetail(toInvoiceView(inv), insts, pays);
    }

    @Transactional
    public InvoiceDetail createInvoice(CreateInvoiceRequest req) {
        Long tenantId = TenantContext.require();
        Invoice inv = new Invoice();
        inv.setTenantId(tenantId);
        inv.setStudentId(req.studentId());
        inv.setCourseId(req.courseId());
        inv.setTitle(req.title() != null ? req.title() : "رسوم دراسية");
        inv.setTotalAmount(req.totalAmount());
        inv.setDiscount(req.discount() != null ? req.discount() : BigDecimal.ZERO);
        inv.setDueDate(req.dueDate());
        invoices.save(inv);
        if (req.installments() != null && !req.installments().isEmpty()) {
            int seq = 1;
            for (InstallmentInput in : req.installments()) {
                Installment inst = new Installment();
                inst.setTenantId(tenantId);
                inst.setInvoiceId(inv.getId());
                inst.setSeq(seq++);
                inst.setAmount(in.amount());
                inst.setDueDate(in.dueDate());
                installments.save(inst);
            }
        }
        return detail(inv.getId());
    }

    @Transactional
    public InvoiceDetail recordPayment(UserPrincipal actor, RecordPaymentRequest req) {
        Long tenantId = TenantContext.require();
        Invoice inv = invoices.findByTenantIdAndId(tenantId, req.invoiceId())
                .orElseThrow(() -> NotFoundException.of("الفاتورة", req.invoiceId()));
        Payment p = new Payment();
        p.setTenantId(tenantId);
        p.setInvoiceId(req.invoiceId());
        p.setInstallmentId(req.installmentId());
        p.setAmount(req.amount());
        p.setMethod(req.method() != null ? req.method() : "CASH");
        p.setReference(req.reference());
        p.setRecordedBy(actor.getId());
        payments.save(p);

        if (req.installmentId() != null) {
            installments.findByTenantIdAndId(tenantId, req.installmentId()).ifPresent(inst -> {
                inst.setStatus("PAID");
                inst.setPaidAt(Instant.now());
                installments.save(inst);
            });
        }
        inv.setPaidAmount(inv.getPaidAmount().add(req.amount()));
        inv.setStatus(resolveStatus(inv));
        invoices.save(inv);

        events.publishEvent(new DomainEvents.PaymentRecorded(tenantId, inv.getStudentId(), inv.getId(), req.amount()));
        return detail(inv.getId());
    }

    /** Scans the tenant's pending installments; marks overdue and raises due reminders (§27). */
    @Transactional
    public int runReminders() {
        Long tenantId = TenantContext.require();
        LocalDate horizon = LocalDate.now().plusDays(7);
        int count = 0;
        for (Installment inst : installments.findByTenantIdAndStatus(tenantId, "PENDING")) {
            if (inst.getDueDate() == null) continue;
            if (inst.getDueDate().isBefore(LocalDate.now())) {
                inst.setStatus("OVERDUE");
                installments.save(inst);
            }
            if (!inst.getDueDate().isAfter(horizon)) {
                invoices.findByTenantIdAndId(tenantId, inst.getInvoiceId()).ifPresent(inv ->
                        events.publishEvent(new DomainEvents.InstallmentDue(tenantId, inv.getStudentId(),
                                inst.getId(), inst.getAmount(), inst.getDueDate().toString())));
                count++;
            }
        }
        return count;
    }

    private String resolveStatus(Invoice inv) {
        BigDecimal net = inv.netAmount();
        if (inv.getPaidAmount().compareTo(net) >= 0) return "PAID";
        if (inv.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) return "PARTIAL";
        return "PENDING";
    }

    private InvoiceView toInvoiceView(Invoice inv) {
        String name = students.findByTenantIdAndId(inv.getTenantId(), inv.getStudentId()).map(s -> s.getFullName()).orElse("—");
        return new InvoiceView(inv.getId(), inv.getStudentId(), name, inv.getCourseId(), inv.getTitle(),
                inv.getTotalAmount(), inv.getDiscount(), inv.getPaidAmount(), inv.remaining(), inv.getStatus(), inv.getDueDate());
    }
}
