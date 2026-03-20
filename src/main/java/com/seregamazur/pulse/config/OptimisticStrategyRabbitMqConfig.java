package com.seregamazur.pulse.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.seregamazur.pulse.shared.outbox.EventType;

@Configuration
@Profile("optimistic")
public class OptimisticStrategyRabbitMqConfig {

    @Bean
    public Declarables orderBinding(Queue orderQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.INVENTORY_FAILED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.PAYMENT_FAILED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.PAYMENT_COMPLETED.name())
        );
    }

    @Bean
    public Declarables inventoryBinding(Queue inventoryQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(inventoryQueue).to(outboxExchange).with(EventType.ORDER_CREATED.name()),
            BindingBuilder.bind(inventoryQueue).to(outboxExchange).with(EventType.PAYMENT_FAILED.name())
        );
    }

    @Bean
    public Declarables paymentBinding(Queue paymentQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(paymentQueue).to(outboxExchange).with(EventType.INVENTORY_RESERVED.name())
        );
    }

}
