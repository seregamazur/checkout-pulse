package com.seregamazur.pulse.inventory;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqSharedConfig;
import com.seregamazur.pulse.inventory.inbox.InventoryInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.event.PaymentCompletedEvent;
import com.seregamazur.pulse.shared.event.PaymentFailedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class InventoryQueueListener {

    private final InventoryInboxRepository repo;
    private final InventoryService inventoryService;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitMqSharedConfig.INVENTORY_QUEUE)
    public void processEvent(@Payload EventEnvelope message) {
        if (repo.findById(message.id()).isPresent()) {
            return;
        }
        try {
            if (message.eventType() == EventType.ORDER_CREATED) {
                inventoryService.onOrderCreated(mapper.readValue(message.payload(), OrderCreatedEvent.class), message.id());
            }
        } catch (OutOfStockException ex) {
            inventoryService.publishFailure(message, ex);
        }
        if (message.eventType() == EventType.PAYMENT_FAILED) {
            inventoryService.onPaymentFailed(mapper.readValue(message.payload(), PaymentFailedEvent.class), message.id());
        } else if (message.eventType() == EventType.PAYMENT_COMPLETED) {
            inventoryService.onPaymentCompleted(mapper.readValue(message.payload(), PaymentCompletedEvent.class), message.id());
        }
    }
}
