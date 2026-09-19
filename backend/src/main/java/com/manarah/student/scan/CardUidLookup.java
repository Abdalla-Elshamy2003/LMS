package com.manarah.student.scan;

import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The fixed factory UID of an RFID/NFC chip, bound to a student beforehand. Stored uppercased. */
@Component
@Order(2)
class CardUidLookup implements StudentLookupStrategy {
    private final StudentRepository students;

    CardUidLookup(StudentRepository students) {
        this.students = students;
    }

    @Override
    public Optional<Student> find(Collection<Long> tenantIds, String code) {
        return students.findByTenantIdInAndCardUid(List.copyOf(tenantIds), code.toUpperCase(Locale.ROOT));
    }
}
