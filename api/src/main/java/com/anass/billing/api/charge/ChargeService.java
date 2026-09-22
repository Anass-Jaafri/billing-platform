package com.anass.billing.api.charge;


import com.anass.billing.api.exception.ChargeNotFoundException;
import com.anass.billing.common.charge.Charge;

import com.anass.billing.common.charge.ChargeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChargeService {


    private final ChargeRepository chargeRepository;

    public ChargeService(ChargeRepository chargeRepository) {
        this.chargeRepository = chargeRepository;
    }

    public Charge createCharge(CreateChargeRequest request) {
        Charge charge = Charge.builder()
                .customerRef(request.customerRef())
                .amount(request.amount())
                .currency(request.currency())
                .build();

        return chargeRepository.save(charge);
    }

    public Charge findById(UUID chargeId){
        return chargeRepository.findById(chargeId)
                .orElseThrow(()-> new ChargeNotFoundException(chargeId));
    }

    public Page<Charge> listCharges(Pageable pageable){
        return chargeRepository.findAll(pageable);
    }
}
