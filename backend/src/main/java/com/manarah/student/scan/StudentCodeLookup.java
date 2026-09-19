package com.manarah.student.scan;

import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The human-readable student code (STD-00042) printed as a barcode on older cards. */
@Component
@Order(3)
class StudentCodeLookup implements StudentLookupStrategy {
    private final StudentRepository students;

    StudentCodeLookup(StudentRepository students) {
        this.students = students;
    }

    @Override
    public Optional<Student> find(Collection<Long> tenantIds, String code) {
        return students.findByTenantIdInAndCode(List.copyOf(tenantIds), code.toUpperCase(Locale.ROOT));
    }
}
