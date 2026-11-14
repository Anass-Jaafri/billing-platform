package com.anass.billing.workers.audit;

import com.anass.billing.common.audit.AuditLog;
import com.anass.billing.common.audit.AuditLogRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @return true if a new entry was written, false if this event was already recorded
     */
    @Transactional
    public boolean record(ChargeEvent event) {
        if (auditLogRepository.existsByEventId(event.eventId())) {
            log.info("Event {} already audited, skipping (redelivery)", event.eventId());
            return false;
        }

        AuditLog entry = AuditLog.forEvent(
                event.eventId(),
                event.data().chargeId(),
                event.eventType(),
                toJson(event));
        auditLogRepository.saveAndFlush(entry);
        log.info("Audited {} for charge {}", event.eventId(), event.data().chargeId());
        return true;
    }

    private String toJson(ChargeEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event" + event.eventId(), e);
        }
    }
}
