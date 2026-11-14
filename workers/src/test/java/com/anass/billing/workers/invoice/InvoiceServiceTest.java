package com.anass.billing.workers.invoice;

import com.anass.billing.common.invoice.Invoice;
import com.anass.billing.common.invoice.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {
    @Mock
    InvoiceRepository invoiceRepository;
    @InjectMocks
    InvoiceService invoiceService;

    @Test
    void generateFor_whenNoInvoiceExists_savesInvoiceWithNextSequenceNumber() {
        UUID chargeId = UUID.randomUUID();
        when(invoiceRepository.existsByChargeId(chargeId)).thenReturn(false);
        when(invoiceRepository.nextInvoiceSequenceValue()).thenReturn(7L);

        boolean created = invoiceService.generateFor(chargeId);

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(saved.capture());
        assertThat(created).isTrue();
        assertThat(saved.getValue().getChargeId()).isEqualTo(chargeId);
        assertThat(saved.getValue().getInvoiceNumber()).isEqualTo("INV-000007");
    }

    @Test
    void generateFor_whenInvoiceAlreadyExists_skipsWithoutConsumingASequenceNumber() {
        UUID chargeId = UUID.randomUUID();
        when(invoiceRepository.existsByChargeId(chargeId)).thenReturn(true);

        boolean created = invoiceService.generateFor(chargeId);

        assertThat(created).isFalse();
        verify(invoiceRepository, never()).nextInvoiceSequenceValue();
        verify(invoiceRepository, never()).saveAndFlush(any());
    }
}
