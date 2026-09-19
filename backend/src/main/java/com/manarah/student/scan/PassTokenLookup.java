package com.manarah.student.scan;

import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The token printed as a QR/barcode on a card or shown on the student's phone. */
@Component
@Order(1)
class PassTokenLookup implements StudentLookupStrategy {
    private final StudentRepository students;

    PassTokenLookup(StudentRepository students) {
        this.students = students;
    }

    @Override
    public Optional<Student> find(Collection<Long> tenantIds, String code) {
        return students.findByTenantIdInAndPassToken(List.copyOf(tenantIds), code);
    }
}
