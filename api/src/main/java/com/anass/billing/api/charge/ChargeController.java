package com.anass.billing.api.charge;


import com.anass.billing.common.charge.Charge;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/charges")
public class ChargeController {

    private final ChargeService chargeService;

    public ChargeController(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    @GetMapping("/{charge-id}")
    public ResponseEntity<ChargeResponse> findChargeById(@PathVariable("charge-id") UUID chargeId) {
        Charge charge = chargeService.findById(chargeId);
        return ResponseEntity.ok(ChargeResponse.from(charge));
    }

    @PostMapping
    public ResponseEntity<ChargeResponse> createCharge(@Valid @RequestBody CreateChargeRequest request){
        Charge charge = chargeService.createCharge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChargeResponse.from(charge));
    }
}
