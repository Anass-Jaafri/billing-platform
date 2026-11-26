package com.anass.billing.api.dlq;

import com.anass.billing.api.exception.UnknownWorkerException;
import com.anass.billing.common.messaging.DlqMessage;
import com.anass.billing.common.messaging.DlqOperations;
import com.anass.billing.common.messaging.DlqWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin: DLQ")
@RestController
@RequestMapping("/admin/dlq")
public class DlqAdminController {
    private final DlqOperations dlqOperations;
    private final ObjectMapper objectMapper;

    public DlqAdminController(DlqOperations dlqOperations, ObjectMapper objectMapper) {
        this.dlqOperations = dlqOperations;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{worker}")
    public List<DlqMessageResponse> list(@PathVariable String worker,
                                         @RequestParam(defaultValue = "50") int limit) {
        return dlqOperations.list(resolve(worker), limit).stream().map(this::toResponse).toList();

    }

    @PostMapping("/{worker}/replay/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replay(@PathVariable String worker, @PathVariable String messageId) {
        dlqOperations.replay(resolve(worker), messageId);
    }

    private DlqWorker resolve(String worker) {
        return DlqWorker.fromPathName(worker).orElseThrow(() -> new UnknownWorkerException(worker));
    }

    private DlqMessageResponse toResponse(DlqMessage message) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.payload());
        } catch (JsonProcessingException e) {
            payload = TextNode.valueOf(message.payload());   // not JSON: show it as text
        }
        return new DlqMessageResponse(message.messageId(), message.failureCount(), payload);
    }
}
