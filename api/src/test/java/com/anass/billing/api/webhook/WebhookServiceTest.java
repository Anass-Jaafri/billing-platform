package com.anass.billing.api.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anass.billing.api.config.RabbitMQConfig;
import com.anass.billing.api.exception.ChargeNotFoundException;
import com.anass.billing.api.exception.DuplicateWebhookException;
import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.status.ChargeStatus;
import com.anass.billing.common.webhook.WebhookEvent;
import com.anass.billing.common.webhook.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    private static final UUID CHARGE_ID = UUID.fromString("eb3560cf-a886-464d-baac-0f26662f5669");
    private static final String EVENT_ID = "evt_1";
    private static final String REDIS_KEY = "webhook:evt_1";

    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private ChargeRepository chargeRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RabbitTemplate rabbitTemplate;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                webhookEventRepository, chargeRepository, new ObjectMapper(), redisTemplate, rabbitTemplate);
        // In production @Transactional switches this on; a plain unit test must do it by hand.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private WebhookPayloadRequest request(String outcome) {
        return new WebhookPayloadRequest(EVENT_ID, CHARGE_ID, outcome);
    }

    private Charge pendingCharge() {
        Charge charge = Charge.builder().customerRef("cust_123").amount(5000).currency("USD").build();
        charge.setId(CHARGE_ID);
        return charge;
    }

    // Everything goes right: no sticky note, charge exists, receipt is accepted
    private Charge stubHappyPath() {
        Charge charge = pendingCharge();
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        when(chargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(charge));
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return charge;
    }

    @Test
    void sickyNoteHit_stopsImmediately_doingNoOtherWork() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(true);

        webhookService.receiveWebhook(request("SUCCEEDED"));

        verifyNoInteractions(chargeRepository, webhookEventRepository, rabbitTemplate);
    }

    @Test
    void unknownCharge_throwsChargeNotFound_andSavesNothing() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        when(chargeRepository.findById(CHARGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.receiveWebhook(request("SUCCEEDED")))
                .isInstanceOf(ChargeNotFoundException.class);

        verifyNoInteractions(webhookEventRepository, rabbitTemplate);
    }

    @Test
    void uniqueViolation_throwsDuplicateWebhookException_andChangesNothing() {
        Charge charge = pendingCharge();
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        when(chargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(charge));
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "dup", new SQLException("duplicate key", "23505")));

        assertThatThrownBy(() -> webhookService.receiveWebhook(request("SUCCEEDED")))
                .isInstanceOf(DuplicateWebhookException.class);

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.PENDING);
        verify(chargeRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void otherIntegrityError_isRethrown_notMistakenForADuplicate() {
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        when(chargeRepository.findById(CHARGE_ID)).thenReturn(Optional.of(pendingCharge()));
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "fk", new SQLException("foreign key violation", "23503")));

        assertThatThrownBy(() -> webhookService.receiveWebhook(request("SUCCEEDED")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateWebhookException.class);
    }

    @Test
    void succeeded_confirmsCharge_publishesConfirmedEvent_andMarksRecordProcessed() {
        Charge charge = stubHappyPath();

        webhookService.receiveWebhook(request("SUCCEEDED"));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        verify(chargeRepository).save(charge);

        ArgumentCaptor<WebhookEvent> savedRecord = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(savedRecord.capture());
        assertThat(savedRecord.getValue().getProcessedAt()).isNotNull();

        ArgumentCaptor<ChargeEvent> published = ArgumentCaptor.forClass(ChargeEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CHARGE_EVENTS_EXCHANGE), eq(""), published.capture());
        assertThat(published.getValue().eventType()).isEqualTo("charge.confirmed");
        assertThat(published.getValue().data().chargeId()).isEqualTo(CHARGE_ID);
    }

    @Test
    void failed_failsCharge_publishesFailedEvent() {
        Charge charge = stubHappyPath();

        webhookService.receiveWebhook(request("FAILED"));

        assertThat(charge.getStatus()).isEqualTo(ChargeStatus.FAILED);

        ArgumentCaptor<ChargeEvent> published = ArgumentCaptor.forClass(ChargeEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.CHARGE_EVENTS_EXCHANGE), eq(""), published.capture());
        assertThat(published.getValue().eventType()).isEqualTo("charge.failed");
    }

    @Test
    void stickyNote_isWrittenOnlyAfterCommit() {
        stubHappyPath();

        webhookService.receiveWebhook(request("SUCCEEDED"));

        // The method has finished, but nothing has committed yet: no note
        verify(redisTemplate, never()).opsForValue();

        // Now press the "commit succeeded" button by hand
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(valueOperations).set(eq(REDIS_KEY), eq(CHARGE_ID.toString()), any(Duration.class));
    }
}