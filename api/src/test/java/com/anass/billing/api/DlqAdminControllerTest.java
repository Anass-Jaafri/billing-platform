package com.anass.billing.api;

import com.anass.billing.api.config.SecurityConfig;
import com.anass.billing.api.dlq.DlqAdminController;
import com.anass.billing.common.messaging.DlqMessage;
import com.anass.billing.common.messaging.DlqMessageNotFoundException;
import com.anass.billing.common.messaging.DlqOperations;
import com.anass.billing.common.messaging.DlqWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DlqAdminController.class)
@Import(SecurityConfig.class)
class DlqAdminControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockBean
    DlqOperations dlqOperations;

    @Test
    void list_returnsMessagesWithParsedPayload() throws Exception {
        when(dlqOperations.list(DlqWorker.EMAIL, 50)).thenReturn(List.of(
                new DlqMessage("m-1", 2, "{\"eventType\":\"charge.confirmed\"}")));

        mockMvc.perform(get("/admin/dlq/email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value("m-1"))
                .andExpect(jsonPath("$[0].failureCount").value(2))
                .andExpect(jsonPath("$[0].payload.eventType").value("charge.confirmed"));
    }

    @Test
    void list_forUnknownWorker_returns404() throws Exception {
        mockMvc.perform(get("/admin/dlq/shipping"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(dlqOperations);
    }

    @Test
    void replay_returns204AndDelegates() throws Exception {
        mockMvc.perform(post("/admin/dlq/email/replay/m-1"))
                .andExpect(status().isNoContent());

        verify(dlqOperations).replay(DlqWorker.EMAIL, "m-1");
    }

    @Test
    void replay_ofAnUnknownMessage_returns404() throws Exception {
        doThrow(new DlqMessageNotFoundException(DlqWorker.EMAIL, "m-9"))
                .when(dlqOperations).replay(DlqWorker.EMAIL, "m-9");

        mockMvc.perform(post("/admin/dlq/email/replay/m-9"))
                .andExpect(status().isNotFound());
    }
}