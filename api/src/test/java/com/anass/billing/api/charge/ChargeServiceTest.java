package com.anass.billing.api.charge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.status.ChargeStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeServiceTest {

    @Mock
    private ChargeRepository chargeRepository;

    @InjectMocks
    private ChargeService chargeService;

    @Test
    void createCharge_savesAndReturnsChargeWithPendingStatus() {
        // Arrange
        CreateChargeRequest request = new CreateChargeRequest("cust_123", 5000, "USD");

        // Mockito doesn't know what save() should return — we tell it to
        // just hand back whatever Charge object it was given, like a real
        // save() would (id/timestamps aside, which we're not asserting here)
        when(chargeRepository.save(any(Charge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Charge result = chargeService.createCharge(request);

        // Assert
        assertThat(result.getCustomerRef()).isEqualTo("cust_123");
        assertThat(result.getAmount()).isEqualTo(5000);
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getStatus()).isEqualTo(ChargeStatus.PENDING);

        // Confirm save() was actually called — not just that our logic
        // happened to produce the right object without persisting it
        verify(chargeRepository).save(any(Charge.class));
    }

    @Test
    void findById_whenChargeExists_returnsCharge() {
        UUID chargeId = UUID.randomUUID();
        Charge existingCharge = Charge.builder()
                .customerRef("cust_123")
                .amount(5000)
                .currency("USD")
                .build();

        when(chargeRepository.findById(chargeId)).thenReturn(Optional.of(existingCharge));

        Charge result = chargeService.findById(chargeId);

        assertThat(result).isEqualTo(existingCharge);
    }

    @Test
    void findById_whenChargeDoesNotExist_throwsChargeNotFoundException() {
        UUID chargeId = UUID.randomUUID();
        when(chargeRepository.findById(chargeId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> chargeService.findById(chargeId))
                .isInstanceOf(com.anass.billing.api.exception.ChargeNotFoundException.class)
                .hasMessageContaining(chargeId.toString());
    }
}