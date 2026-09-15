package com.manarah.payment;

import com.manarah.payment.PaymentDtos.InvoiceView;
import com.manarah.security.UserPrincipal;
import com.manarah.student.StudentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/family")
@PreAuthorize("hasRole('PARENT')")
public class FamilyFinanceController {
    private final StudentService students;
    private final PaymentService payments;

    public FamilyFinanceController(StudentService students, PaymentService payments) {
        this.students = students; this.payments = payments;
    }

    public record ChildFinance(Long studentId, String studentName, BigDecimal total, BigDecimal paid,
                               BigDecimal remaining, List<InvoiceView> invoices) {}

    @GetMapping("/finance")
    public List<ChildFinance> finance(@AuthenticationPrincipal UserPrincipal actor) {
        return students.children(actor).stream().map(child -> {
            List<InvoiceView> invoices = payments.byStudent(child.id());
            BigDecimal total = invoices.stream().map(InvoiceView::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = invoices.stream().map(InvoiceView::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = invoices.stream().map(InvoiceView::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ChildFinance(child.id(), child.fullName(), total, paid, remaining, invoices);
        }).toList();
    }
}
