package com.seregamazur.pulse;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.seregamazur.pulse.shared.event.OrderUpdatedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;
import com.seregamazur.pulse.shared.outbox.ScheduledOutboxPublisher;
import com.seregamazur.pulse.shared.outbox.OutboxRecord;
import com.seregamazur.pulse.shared.outbox.OutboxRepository;
import com.seregamazur.pulse.shared.outbox.OutboxType;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=false",
        "outbox.mode=polling"
    }
)
@Import(TestcontainersConfiguration.class)
class OutboxPublisherIntegrationTest {

    @Autowired ScheduledOutboxPublisher outboxPublisher;
    @Autowired OutboxRepository outboxRepository;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("publish() sends events to RabbitMQ and deletes records from outbox")
    void publish_sendsEventsAndDeletesRecords() {
        UUID orderId = UUID.randomUUID();
        OutboxRecord record = new OutboxRecord(
            UUID.randomUUID(), OutboxType.ORDER, orderId,
            EventType.ORDER_UPDATED,
            objectMapper.writeValueAsString(new OrderUpdatedEvent(orderId)));
        outboxRepository.save(record);

        assertThat(outboxRepository.count()).isEqualTo(1);

        outboxPublisher.publish();

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("publish() with multiple records — all published and deleted")
    void publish_multipleRecords_allDeleted() {
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();

        outboxRepository.saveAll(List.of(
            new OutboxRecord(UUID.randomUUID(), OutboxType.ORDER, order1,
                EventType.ORDER_UPDATED,
                objectMapper.writeValueAsString(new OrderUpdatedEvent(order1))),
            new OutboxRecord(UUID.randomUUID(), OutboxType.ORDER, order2,
                EventType.ORDER_UPDATED,
                objectMapper.writeValueAsString(new OrderUpdatedEvent(order2)))
        ));

        assertThat(outboxRepository.count()).isEqualTo(2);

        outboxPublisher.publish();

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("publish() on empty outbox is a no-op")
    void publish_emptyOutbox_doesNothing() {
        assertThat(outboxRepository.count()).isZero();
        outboxPublisher.publish();
        assertThat(outboxRepository.count()).isZero();
    }
}
