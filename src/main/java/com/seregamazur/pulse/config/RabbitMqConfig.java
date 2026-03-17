package com.seregamazur.pulse.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seregamazur.pulse.shared.outbox.EventType;

@Configuration
public class RabbitMqConfig {
    public static final String OUTBOX_EXCHANGE = "outbox-exchange";

    public static final String ORDER_QUEUE = "order.queue";
    public static final String INVENTORY_QUEUE = "inventory.queue";
    public static final String PAYMENT_QUEUE = "payment.queue";

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(ORDER_QUEUE, true);
    }

    @Bean
    public Queue inventoryQueue() {
        return new Queue(INVENTORY_QUEUE, true);
    }

    @Bean
    public Queue paymentQueue() {
        return new Queue(PAYMENT_QUEUE, true);
    }

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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }


}