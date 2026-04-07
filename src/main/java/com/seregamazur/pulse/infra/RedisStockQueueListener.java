package com.seregamazur.pulse.infra;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqSharedConfig;
import com.seregamazur.pulse.infra.inbox.RedisStockInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.InventoryReleasedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RedisStockQueueListener {

    private final RedisStockProvider stockProvider;
    private final RedisStockInboxRepository inboxRepository;
    private final ObjectMapper mapper;

    @RabbitListener(queues = RabbitMqSharedConfig.REDIS_STOCK_QUEUE)
    public void processEvent(@Payload EventEnvelope message) {
        if (inboxRepository.findById(message.id()).isPresent()) {
            return;
        }
        if (message.eventType() == EventType.STOCK_RESERVED) {
            stockProvider.updateRedisStock(mapper.readValue(message.payload(), InventoryReservedEvent.class).items(), message.id());
        } else if (message.eventType() == EventType.STOCK_RELEASED) {
            stockProvider.updateRedisStock(mapper.readValue(message.payload(), InventoryReleasedEvent.class).items(), message.id());
        } else if (message.eventType() == EventType.ORDER_CREATED) {
            stockProvider.cleanRedisAndDbCartAndReserved(mapper.readValue(message.payload(), OrderCreatedEvent.class), message.id());
        }

    }
}
