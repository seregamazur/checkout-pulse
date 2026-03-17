package com.seregamazur.pulse.payment;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqConfig;
import com.seregamazur.pulse.payment.inbox.PaymentInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PaymentQueueListener {

    private final PaymentInboxRepository repo;
    private final PaymentService paymentService;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitMqConfig.PAYMENT_QUEUE)
    public void processEvent(@Payload EventEnvelope message) {
        if (repo.findById(message.id()).isPresent()) {
            return;
        }
        if (message.eventType() == EventType.INVENTORY_RESERVED) {
            paymentService.processPayment(
                mapper.readValue(message.payload(), InventoryReservedEvent.class),
                message.id()
            );
        }
    }
}
