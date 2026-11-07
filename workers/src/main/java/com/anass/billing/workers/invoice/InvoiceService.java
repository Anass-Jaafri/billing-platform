package com.anass.billing.workers.invoice;

import com.anass.billing.common.invoice.Invoice;
import com.anass.billing.common.invoice.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * @Return true if a new invoice was created, false if one already existed
     */
    @Transactional
    public boolean generateFor(UUID chargeId) {
        if (invoiceRepository.existsByChargeId(chargeId)) {
            log.info("Invoice already exists for charge {}, skipping (redelivery)", chargeId);
            return false;
        }
        long sequenceValue = invoiceRepository.nextInvoiceSequenceValue();
        Invoice invoice = Invoice.forCharge(chargeId, sequenceValue);
        invoiceRepository.saveAndFlush(invoice);
        log.info("Generated invoice {} for charge {}", invoice.getInvoiceNumber(), chargeId);
        return true;
    }

}
