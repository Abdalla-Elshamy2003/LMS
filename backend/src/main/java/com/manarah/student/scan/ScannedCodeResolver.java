package com.manarah.student.scan;

import com.manarah.student.domain.Student;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Resolves a scanned code by trying each {@link StudentLookupStrategy} in order - at most three indexed lookups. */
@Component
public class ScannedCodeResolver {
    private final List<StudentLookupStrategy> strategies;

    public ScannedCodeResolver(List<StudentLookupStrategy> strategies) {
        this.strategies = strategies;
    }

    public Optional<Student> resolve(Collection<Long> tenantIds, String raw) {
        String code = ScannedCode.normalize(raw);
        if (code.isEmpty()) return Optional.empty();
        return strategies.stream()
                .map(strategy -> strategy.find(tenantIds, code))
                .flatMap(Optional::stream)
                .findFirst();
    }
}
