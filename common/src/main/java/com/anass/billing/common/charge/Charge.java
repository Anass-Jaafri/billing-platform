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

}
