package com.manarah.student.pass;

import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The secret behind a student's QR. Minted lazily - the first time a pass is needed, whether that is
 * the student opening their profile, staff printing a card, or the welcome email at registration.
 */
@Component
public class StudentPassTokens {
    private final StudentRepository students;

    public StudentPassTokens(StudentRepository students) {
        this.students = students;
    }

    /** Returns the student with a pass token, generating and saving one if they do not have it yet. */
    public Student ensure(Student student) {
        if (student.getPassToken() == null || student.getPassToken().isBlank()) {
            student.setPassToken(UUID.randomUUID().toString().replace("-", ""));
            students.save(student);
        }
        return student;
    }
}
