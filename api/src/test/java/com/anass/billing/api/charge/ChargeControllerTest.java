package com.anass.billing.api.charge;


import com.anass.billing.api.config.SecurityConfig;
import com.anass.billing.api.exception.ChargeNotFoundException;
import com.anass.billing.common.charge.Charge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(ChargeController.class)
@Import(SecurityConfig.class)
public class ChargeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Spring Boot auto-configures this for JSON conversion

    @MockBean
    private ChargeService chargeService; // fake — the real one needs a repository we don't have here

    @Test
    void createCharge_withValidRequest_returns201() throws Exception {
        CreateChargeRequest request = new CreateChargeRequest("cust_123", 5000, "USD");
        Charge savedCharge = Charge.builder()
                .customerRef("cust_123")
                .amount(5000)
                .currency("USD")
                .build();
        when(chargeService.createCharge(any(CreateChargeRequest.class))).thenReturn(savedCharge);
        mockMvc.perform(post("/charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerRef").value("cust_123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
    @Test
    void createCharge_withNegativeAmount_returns400WithFieldError() throws Exception {
        // amount: -5 violates @Positive — chargeService is never even called
        CreateChargeRequest request = new CreateChargeRequest("cust_123", -5, "USD");

        mockMvc.perform(post("/charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }
    @Test
    void createCharge_withInvalidCurrency_returns400() throws Exception {
        CreateChargeRequest request = new CreateChargeRequest("cust_123", 5000, "usd"); // lowercase, violates @Pattern

        mockMvc.perform(post("/charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currency"));
    }

    @Test
    void findChargeById_whenFound_returns200() throws Exception {
        UUID chargeId = UUID.randomUUID();
        Charge charge = Charge.builder()
                .customerRef("cust_123")
                .amount(5000)
                .currency("USD")
                .build();

        when(chargeService.findById(chargeId)).thenReturn(charge);

        mockMvc.perform(get("/charges/{charge-id}", chargeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerRef").value("cust_123"));
    }

    @Test
    void findChargeById_whenNotFound_returns404() throws Exception {
        UUID chargeId = UUID.randomUUID();
        when(chargeService.findById(chargeId)).thenThrow(new ChargeNotFoundException(chargeId));

        mockMvc.perform(get("/charges/{charge-id}", chargeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Could not find charge with id: " + chargeId));
    }
    @Test
    void listCharges_returnsPagedResponse() throws Exception {
        Charge charge = Charge.builder()
                .customerRef("cust_123")
                .amount(5000)
                .currency("USD")
                .build();

        Page<Charge> page = new PageImpl<>(
                java.util.List.of(charge),
                PageRequest.of(0, 20),
                1
        );

        when(chargeService.listCharges(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/charges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerRef").value("cust_123"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
}
