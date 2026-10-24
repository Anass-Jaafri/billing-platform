package com.anass.billing.api.webhook;

import com.anass.billing.api.config.RabbitMQConfig;
import com.anass.billing.api.exception.ChargeNotFoundException;
import com.anass.billing.api.exception.DuplicateWebhookException;
import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.webhook.WebhookEvent;
import com.anass.billing.common.webhook.WebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    // 10 minutes for local dev. In a real payment system, match the provider's
    // retry window (e.g. Stripe retries for up to 3 days).
    private static final Duration IDEMPOTENCY_KEY_TTL = Duration.ofMinutes(10);
    private static final String IDEMPOTENCY_KEY_PREFIX = "webhook:";

    // PostgreSQL's code for "unique rule broken". A foreign-key break is 23503.
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private final WebhookEventRepository webhookEventRepository;
    private final ChargeRepository chargeRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    public WebhookService(
            WebhookEventRepository webhookEventRepository,
            ChargeRepository chargeRepository,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            RabbitTemplate rabbitTemplate) {
        this.webhookEventRepository = webhookEventRepository;
        this.chargeRepository = chargeRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void receiveWebhook(WebhookPayloadRequest request) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + request.eventId();

        // 1. Sticky note: seen recently? Then stop.
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            return;
        }

        // 2. The charge must exist. Say so clearly (404) instead of letting a
        //    database error be mistaken for a duplicate.
        Charge charge = chargeRepository.findById(request.chargeId())
                .orElseThrow(() -> new ChargeNotFoundException(request.chargeId()));

        // 3. File the receipt, inside this same bundle.
        WebhookEvent webhookEvent = saveWebhookEvent(request);

        // 4. Update the charge and prepare the announcement.
        ChargeEvent chargeEvent;
        if ("SUCCEEDED".equals(request.outcome())) {
            charge.confirm();
            chargeEvent = ChargeEvent.confirmed(charge);
        } else {
            charge.fail();
            chargeEvent = ChargeEvent.failed(charge);
        }
        chargeRepository.save(charge);

        // 5. Mark the receipt as fully handled.
        webhookEvent.setProcessedAt(LocalDateTime.now());
        webhookEventRepository.save(webhookEvent);

        // 6. Queue the announcement. Held until the bundle commits.
        rabbitTemplate.convertAndSend(RabbitMQConfig.CHARGE_EVENTS_EXCHANGE, "", chargeEvent);

        // 7. Sticky note: only written once the bundle has really committed.
        writeStickyNoteAfterCommit(redisKey, request.chargeId().toString());
    }

    private WebhookEvent saveWebhookEvent(WebhookPayloadRequest request) {
        WebhookEvent event = WebhookEvent.builder()
                .providerEventId(request.eventId())
                .chargeId(request.chargeId())
                .payload(toJson(request))
                .build();
        try {
            // saveAndFlush sends the INSERT now, so a refusal happens right here.
            return webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new DuplicateWebhookException(request.eventId());
            }
            throw e; // any other database refusal is a real problem, not a duplicate
        }
    }

    private boolean isUniqueViolation(DataIntegrityViolationException e) {
        Throwable root = e.getMostSpecificCause();
        return root instanceof SQLException sql
                && UNIQUE_VIOLATION_SQLSTATE.equals(sql.getSQLState());
    }

    private void writeStickyNoteAfterCommit(String redisKey, String value) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    redisTemplate.opsForValue().set(redisKey, value, IDEMPOTENCY_KEY_TTL);
                } catch (RuntimeException e) {
                    log.warn("Could not write idempotency key {}", redisKey, e);
                }
            }
        });
    }

    private String toJson(WebhookPayloadRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }
}