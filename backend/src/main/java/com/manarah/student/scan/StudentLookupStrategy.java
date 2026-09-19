package com.manarah.student.scan;

import com.manarah.student.domain.Student;

import java.util.Collection;
import java.util.Optional;

/**
 * One way a scanned code can identify a student. The academy does not choose what its card readers
 * emit, so several interchangeable lookups exist; {@link ScannedCodeResolver} tries them in
 * {@code @Order} sequence. A new kind of card is a new implementation, not an edit to GateService.
 */
public interface StudentLookupStrategy {

    /** Finds the student this code identifies within the given tenants, if any. {@code code} is already normalized. */
    Optional<Student> find(Collection<Long> tenantIds, String code);
}
