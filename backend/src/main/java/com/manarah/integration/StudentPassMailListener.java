package com.manarah.integration;

import com.manarah.common.events.DomainEvents;
import com.manarah.student.pass.StudentPassEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the welcome QR email once a registration has committed. Asynchronous on purpose: an SMTP
 * handshake can take seconds and a mail failure must never slow down or fail the signup itself.
 */
@Component
public class StudentPassMailListener {
    private static final Logger log = LoggerFactory.getLogger(StudentPassMailListener.class);

    private final StudentPassEmailService passEmails;

    public StudentPassMailListener(StudentPassEmailService passEmails) {
        this.passEmails = passEmails;
    }

    @Async
    @TransactionalEventListener
    public void onStudentRegistered(DomainEvents.StudentRegistered event) {
        try {
            passEmails.sendPass(event.tenantId(), event.studentId());
        } catch (RuntimeException e) {
            log.warn("student_pass_email outcome=error studentId={} cause={}", event.studentId(), e.toString());
        }
    }
}
