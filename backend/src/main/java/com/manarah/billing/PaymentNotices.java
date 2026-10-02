package com.manarah.billing;

import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.repo.UserRepository;
import com.manarah.notification.NotificationService;
import com.manarah.notification.NotificationDtos.NotifyCommand;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** In-app notices about payments: to head office (who reviews them) and to the student who paid. */
@Component
public class PaymentNotices {
    private final TeacherAcademyRepository academies;
    private final UserRepository users;
    private final StudentRepository students;
    private final NotificationService notifications;

    public PaymentNotices(TeacherAcademyRepository academies, UserRepository users, StudentRepository students, NotificationService notifications) {
        this.academies = academies; this.users = users; this.students = students; this.notifications = notifications;
    }

    /** Every owner and branch admin of the head office that runs this teacher's space. */
    public void headOffice(Long teacherTenantId, String title, String body) {
        Long headOffice = academies.findByTenantId(teacherTenantId).map(TeacherAcademy::getManagerTenantId).orElse(null);
        if (headOffice == null) return;
        for (Role role : List.of(Role.SUPER_ADMIN, Role.BRANCH_ADMIN))
            users.findByTenantIdAndRole(headOffice, role).forEach(u -> notifications.notify(headOffice,
                    new NotifyCommand(u.getId(), null, title, body, "PAYMENT", "PaymentSubmission", null, List.of("IN_APP"))));
    }

    /** The student behind this seat, in the teacher's space the payment belongs to. */
    public void student(Long tenantId, Long studentId, String title, String body, Long invoiceId) {
        students.findById(studentId).filter(s -> s.getUserId() != null).ifPresent(s -> notifications.notify(tenantId,
                new NotifyCommand(s.getUserId(), null, title, body, "PAYMENT", "PaymentInvoice", invoiceId, List.of("IN_APP"))));
    }
}
