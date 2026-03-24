package com.seregamazur.pulse.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqSharedConfig {

    public static final String OUTBOX_EXCHANGE = "outbox-exchange";

    public static final String ORDER_QUEUE = "order.queue";
    public static final String ORDER_VIEW_QUEUE = "order-view.queue";
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
    public Queue orderViewQueue() {
        return new Queue(ORDER_VIEW_QUEUE, true);
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
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
