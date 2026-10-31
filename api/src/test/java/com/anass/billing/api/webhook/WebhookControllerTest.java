package com.anass.billing.api.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anass.billing.api.config.SecurityConfig;
import com.anass.billing.api.exception.ChargeNotFoundException;
import com.anass.billing.api.exception.DuplicateWebhookException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(WebhookController.class)
@Import(SecurityConfig.class)
class WebhookControllerTest {

    private static final UUID CHARGE_ID = UUID.fromString("eb3560cf-a886-464d-baac-0f26662f5669");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private WebhookService webhookService;

    private ResultActions postWebhook(String body) throws Exception {
        return mockMvc.perform(post("/webhooks/payment-provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String validBody() throws Exception {
        return objectMapper.writeValueAsString(new WebhookPayloadRequest("evt_1", CHARGE_ID, "SUCCEEDED"));
    }

    @Test
    void validPayload_returns200_andReachesTheService() throws Exception {
        postWebhook(validBody()).andExpect(status().isOk());

        verify(webhookService).receiveWebhook(any(WebhookPayloadRequest.class));
    }

    @Test
    void badOutcome_returns400_andServiceIsNeverCalled() throws Exception {
        String body = objectMapper.writeValueAsString(new WebhookPayloadRequest("evt_1", CHARGE_ID, "BANANA"));

        postWebhook(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("outcome"));

        verifyNoInteractions(webhookService);
    }

    @Test
    void missingChargeId_returns400() throws Exception {
        postWebhook("{\"eventId\":\"evt_1\",\"outcome\":\"SUCCEEDED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("chargeId"));
    }

    @Test
    void duplicateWebhook_returns200() throws Exception {
        doThrow(new DuplicateWebhookException("evt_1"))
                .when(webhookService).receiveWebhook(any(WebhookPayloadRequest.class));

        postWebhook(validBody()).andExpect(status().isOk());
    }

    @Test
    void unknownCharge_returns404WithMessage() throws Exception {
        doThrow(new ChargeNotFoundException(CHARGE_ID))
                .when(webhookService).receiveWebhook(any(WebhookPayloadRequest.class));

        postWebhook(validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Could not find charge with id: " + CHARGE_ID));
    }
}