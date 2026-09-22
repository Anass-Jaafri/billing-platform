package com.anass.billing.common.charge;


import com.anass.billing.common.status.ChargeStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeTest {

     @Test
     void confirm_whenPending_transitionsToConfirmed(){
         // Arrange — set up the object under test
         Charge charge = Charge.builder()
                 .customerRef("cust_123")
                 .amount(5000)
                 .currency("USD")
                 .build(); // status defaults to PENDING

         // Act — do the thing we're testing
         charge.confirm();

         // Assert — check the result
         assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
     }

     @Test
    void confirm_whenAlreadyConfirmed_throwsIllegalStateException(){
         Charge charge = Charge.builder()
                 .customerRef("cust_123")
                 .amount(5000)
                 .currency("USD")
                 .build();
         charge.confirm(); // now CONFIRMED

         assertThatThrownBy(charge::confirm)
                 .isInstanceOf(IllegalStateException.class)
                 .hasMessageContaining("Cannot confirm");
     }
    @Test
    void fail_whenPending_transitionsToFailed() {
        Charge charge = Charge.builder()
                .customerRef("cust_123")
                .amount(5000)
                .currency("USD")
                .build();

        charge.fail();

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);
    }
}
