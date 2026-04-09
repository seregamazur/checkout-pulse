package com.seregamazur.pulse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;
import com.seregamazur.pulse.shared.outbox.OutboxRecord;
import com.seregamazur.pulse.shared.outbox.OutboxRepository;
import com.seregamazur.pulse.shared.outbox.OutboxType;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
    "spring.docker.compose.enabled=false",
    "spring.flyway.enabled=false"
})
class DebeziumCdcTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void debeziumShouldDetectOutboxInsertAndDeleteAfterPublishing() throws Exception {
        // Let Debezium engine fully start and set up the replication slot
        Thread.sleep(15_000);

        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
            UUID.randomUUID(), orderId, List.of(), BigDecimal.TEN);

        OutboxRecord record = new OutboxRecord(
            UUID.randomUUID(), OutboxType.ORDER, orderId,
            EventType.ORDER_CREATED, objectMapper.writeValueAsString(event));

        outboxRepository.saveAndFlush(record);
        UUID recordId = record.getId();

        boolean processed = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1_000);
            if (!outboxRepository.existsById(recordId)) {
                processed = true;
                break;
            }
        }

        assertTrue(processed, "Outbox record should be deleted by Debezium CDC publisher within 30s");
    }
}
