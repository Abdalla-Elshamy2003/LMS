package com.manarah.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findAllByOrderBySortOrderAsc();
    Optional<PaymentMethod> findByCode(String code);
}
