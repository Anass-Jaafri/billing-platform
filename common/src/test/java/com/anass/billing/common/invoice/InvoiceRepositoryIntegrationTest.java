package com.anass.billing.common.invoice;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class InvoiceRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Autowired
    InvoiceRepository invoiceRepository;
    @Autowired
    ChargeRepository chargeRepository;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private Charge savedCharge() {
        Charge charge = Charge.builder()
                .customerRef("cust_123")
                .amount(5000)
                .currency("USD")
                .build(); // status defaults to PENDING via @Builder.Default

        return chargeRepository.saveAndFlush(charge);
    }

    @Test
    void save_thenFindByChargeId_returnsInvoice() {
        Charge charge = savedCharge();
        long seq = invoiceRepository.nextInvoiceSequenceValue();

        invoiceRepository.saveAndFlush(Invoice.forCharge(charge.getId(), seq));

        assertThat(invoiceRepository.existsByChargeId(charge.getId())).isTrue();
        assertThat(invoiceRepository.findByChargeId(charge.getId()))
                .get().extracting(Invoice::getInvoiceNumber)
                .isEqualTo(String.format("INV-%06d", seq));
    }

    @Test
    void nextInvoiceSequenceValue_increasesEachCall() {
        long first = invoiceRepository.nextInvoiceSequenceValue();
        long second = invoiceRepository.nextInvoiceSequenceValue();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void secondInvoiceForSameCharge_isRejectedByUniqueConstraint() {
        Charge charge = savedCharge();
        invoiceRepository.saveAndFlush(
                Invoice.forCharge(charge.getId(), invoiceRepository.nextInvoiceSequenceValue()));

        Invoice duplicate = Invoice.forCharge(charge.getId(), invoiceRepository.nextInvoiceSequenceValue());

        assertThatThrownBy(() -> invoiceRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invoiceForUnknownCharge_isRejectedByForeignKey() {
        Invoice orphan = Invoice.forCharge(UUID.randomUUID(), invoiceRepository.nextInvoiceSequenceValue());

        assertThatThrownBy(() -> invoiceRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}