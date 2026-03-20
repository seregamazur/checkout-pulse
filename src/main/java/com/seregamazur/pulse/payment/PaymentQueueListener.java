package com.seregamazur.pulse.payment;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqSharedConfig;
import com.seregamazur.pulse.payment.inbox.PaymentInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.InventoryFailedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PaymentQueueListener {

    private final PaymentInboxRepository repo;
    private final PaymentService paymentService;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitMqSharedConfig.PAYMENT_QUEUE)
    public void processEvent(@Payload EventEnvelope message) {
        if (repo.findById(message.id()).isPresent()) {
            return;
        }
        if (message.eventType() == EventType.INVENTORY_RESERVED) {
            paymentService.processPayment(
                mapper.readValue(message.payload(), InventoryReservedEvent.class),
                message.id()
            );
        } else if (message.eventType() == EventType.INVENTORY_FAILED) {
            paymentService.onInventoryFailed(
                mapper.readValue(message.payload(), InventoryFailedEvent.class),
                message.id());
        } else if (message.eventType() == EventType.ORDER_CREATED) {
            paymentService.onOrderCreated(
                mapper.readValue(message.payload(), OrderCreatedEvent.class),
                message.id());
        }
    }
}
