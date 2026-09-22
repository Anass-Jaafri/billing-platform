package com.anass.billing.common.charge;

import com.anass.billing.common.BaseEntity;
import com.anass.billing.common.status.ChargeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "charges")
public class Charge extends BaseEntity {

    @Column(nullable = false)
    @Setter
    private String customerRef;
    @Column(nullable = false)
    @Setter
    private long amount;
    @Column(nullable = false, length = 3)
    @Setter
    private String currency;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ChargeStatus status =  ChargeStatus.PENDING;

    public void confirm(){
        if(this.status != ChargeStatus.PENDING){
            throw new IllegalStateException(
                    "Cannot confirm a charge in status" +this.status
            );
        }
        this.status = ChargeStatus.CONFIRMED;
    }

    public void fail(){
        if(this.status != ChargeStatus.PENDING){
            throw new IllegalStateException("Cannot fail a charge in status" +this.status
            );

        }
        this.status = ChargeStatus.FAILED;
    }

}
