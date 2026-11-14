package com.anass.billing.common.invoice;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {

    @Test
    void forCharge_formatsNumberWithPadding() {
        UUID chargeId = UUID.randomUUID();

        Invoice invoice = Invoice.forCharge(chargeId, 42);

        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-000042");
        assertThat(invoice.getChargeId()).isEqualTo(chargeId);
        assertThat(invoice.getGeneratedAt()).isNotNull();
    }

    @Test
    void forCharge_beyondSixDigits_stillWorks() {
        Invoice invoice = Invoice.forCharge(UUID.randomUUID(), 1_234_567);

        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-1234567");
    }

    @Test
    void forCharge_withNullChargeId_throws() {
        assertThatThrownBy(() -> Invoice.forCharge(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forCharge_withNonPositiveSequence_throws() {
        assertThatThrownBy(() -> Invoice.forCharge(UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
