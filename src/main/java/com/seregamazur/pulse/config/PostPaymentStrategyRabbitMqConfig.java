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
@Profile("post-payment")
public class PostPaymentStrategyRabbitMqConfig {

    @Bean
    public Declarables orderBinding(Queue orderQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.STOCK_RESERVED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.STOCK_RELEASED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.STOCK_FAILED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.PAYMENT_REFUNDED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.PAYMENT_PROCESSED.name()),
            BindingBuilder.bind(orderQueue).to(outboxExchange).with(EventType.PAYMENT_FAILED.name())
        );
    }

    @Bean
    public Declarables orderViewBinding(Queue orderViewQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(orderViewQueue).to(outboxExchange).with(EventType.ORDER_UPDATED.name())
        );
    }

    @Bean
    public Declarables inventoryBinding(Queue inventoryQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(inventoryQueue).to(outboxExchange).with(EventType.PAYMENT_PROCESSED.name())
        );
    }

    @Bean
    public Declarables paymentBinding(Queue paymentQueue, TopicExchange outboxExchange) {
        return new Declarables(
            BindingBuilder.bind(paymentQueue).to(outboxExchange).with(EventType.STOCK_FAILED.name()),
            BindingBuilder.bind(paymentQueue).to(outboxExchange).with(EventType.ORDER_CREATED.name())
        );
    }

}
