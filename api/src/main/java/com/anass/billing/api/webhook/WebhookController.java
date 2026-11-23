package com.anass.billing.api.webhook;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Webhooks")
@SecurityRequirements
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/payment-provider")
    public ResponseEntity<Void> receiveWebhook(
            @Valid @RequestBody WebhookPayloadRequest request
    ) {
        webhookService.receiveWebhook(request);
        return ResponseEntity.ok().build();
    }
}
