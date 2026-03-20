package com.seregamazur.pulse.order;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqSharedConfig;
import com.seregamazur.pulse.order.inbox.OrderInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.InventoryFailedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.PaymentCompletedEvent;
import com.seregamazur.pulse.shared.event.PaymentFailedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderQueueListener {

    private final OrderInboxRepository repo;
    private final OrderService orderService;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitMqSharedConfig.ORDER_QUEUE)
    public void processEvent(@Payload EventEnvelope message) {
        if (repo.findById(message.id()).isPresent()) {
            return;
        }
        if (message.eventType() == EventType.PAYMENT_COMPLETED) {
            orderService.onPaymentCompleted(mapper.readValue(message.payload(), PaymentCompletedEvent.class), message.id());
        } else if (message.eventType() == EventType.PAYMENT_FAILED) {
            orderService.onPaymentFailed(mapper.readValue(message.payload(), PaymentFailedEvent.class), message.id());
        } else if (message.eventType() == EventType.INVENTORY_FAILED) {
            orderService.onInventoryFailed(mapper.readValue(message.payload(), InventoryFailedEvent.class), message.id());
        } else if (message.eventType() == EventType.INVENTORY_RESERVED) {
            orderService.onInventoryReserved(mapper.readValue(message.payload(), InventoryReservedEvent.class), message.id());
        }

    }
}
