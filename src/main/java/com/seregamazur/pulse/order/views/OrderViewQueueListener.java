package com.seregamazur.pulse.order.views;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqSharedConfig;
import com.seregamazur.pulse.order.views.inbox.OrderViewInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.OrderUpdatedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderViewQueueListener {

    private final OrderProjectionHandler projectionHandler;
    private final OrderViewInboxRepository repo;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitMqSharedConfig.ORDER_VIEW_QUEUE)
    public void processEvent(@Payload EventEnvelope message) {
        if (repo.findById(message.id()).isPresent()) {
            return;
        }
        if (message.eventType() == EventType.ORDER_UPDATED) {
            projectionHandler.onOrderUpdate(mapper.readValue(message.payload(), OrderUpdatedEvent.class), message.id());
        }
    }
}
