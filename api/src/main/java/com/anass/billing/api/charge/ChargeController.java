package com.anass.billing.api.charge;


import com.anass.billing.common.PagedResponse;
import com.anass.billing.common.charge.Charge;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    @PostMapping
    public ResponseEntity<ChargeResponse> createCharge(@Valid @RequestBody CreateChargeRequest request){
        Charge charge = chargeService.createCharge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChargeResponse.from(charge));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ChargeResponse>> listCharges(@PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable){
        Page<Charge> charges = chargeService.listCharges(pageable);
        return ResponseEntity.ok(PagedResponse.from(charges,ChargeResponse::from));
    }
    @GetMapping("/{charge-id}")
    public ResponseEntity<ChargeResponse> findChargeById(@PathVariable("charge-id") UUID chargeId) {
        Charge charge = chargeService.findById(chargeId);
        return ResponseEntity.ok(ChargeResponse.from(charge));
    }
}
