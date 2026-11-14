package com.anass.billing.workers.audit;

import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.Messaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditListener {

    private final AuditService auditService;

    public AuditListener(AuditService auditService){
        this.auditService = auditService;
    }

    @RabbitListener(queues = Messaging.AUDIT_QUEUE)
    public void onChargeEvent(ChargeEvent event){
        auditService.record(event);
    }
}
