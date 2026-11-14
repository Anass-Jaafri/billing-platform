package com.anass.billing.common.invoice;

import com.anass.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Invoice extends BaseEntity {
    private static final String NUMBER_FORMAT = "INV-%06d";

    @Column(name = "charge_id", nullable = false, updatable = false)
    private UUID chargeId;

    @Column(name = "invoice_number", nullable = false, updatable = false)
    private String invoiceNumber;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    public static Invoice forCharge(UUID chargeId, long sequenceValue) {
        if (chargeId == null) {
            throw new IllegalArgumentException("chargeId is required");
        }
        if (sequenceValue < 1) {
            throw new IllegalArgumentException("sequenceValue must be positive");
        }

        Invoice invoice = new Invoice();
        invoice.chargeId = chargeId;
        invoice.invoiceNumber = String.format(NUMBER_FORMAT, sequenceValue);
        invoice.generatedAt = Instant.now();
        return invoice;
    }
}
