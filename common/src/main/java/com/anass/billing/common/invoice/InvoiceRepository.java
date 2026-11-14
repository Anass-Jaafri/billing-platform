package com.anass.billing.common.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    boolean existsByChargeId(UUID chargeId);

    Optional<Invoice> findByChargeId(UUID chargeId);

    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceSequenceValue();
}
