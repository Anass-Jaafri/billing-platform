package com.anass.billing.api.charge;


import com.anass.billing.common.charge.Charge;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "Charge not found"));
    }
}
