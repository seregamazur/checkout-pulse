package com.seregamazur.pulse.shared.outbox;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.config.RabbitMqSharedConfig;
import com.seregamazur.pulse.shared.event.EventEnvelope;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CDC-based outbox publisher using Debezium Embedded Engine.
 * Monitors the shared.outbox table via PostgreSQL WAL and publishes
 * captured INSERT events to RabbitMQ in near-real-time.
 * Replaces the polling-based {@link ScheduledOutboxPublisher}.
 */
@Component
@ConditionalOnProperty(name = "outbox.mode", havingValue = "cdc", matchIfMissing = true)
@Slf4j
public class DebeziumOutboxPublisher implements SmartLifecycle {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private ExecutorService executor;
    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private volatile boolean running = false;

    public DebeziumOutboxPublisher(
        RabbitTemplate rabbitTemplate,
        OutboxRepository outboxRepository,
        ObjectMapper objectMapper,
        DataSource dataSource) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadExecutor();
        engine = DebeziumEngine.create(Json.class)
            .using(buildProperties())
            .notifying((records, committer) -> {
                for (var record : records) {
                    try {
                        processRecord(record);
                    } catch (Exception e) {
                        log.error("Failed to process CDC event: {}", e.getMessage(), e);
                    }
                    committer.markProcessed(record);
                }
                committer.markBatchFinished();
            })
            .build();

        executor.execute(engine);
        running = true;
        log.info("Debezium CDC outbox publisher started");
    }

    @Override
    public void stop() {
        try {
            if (engine != null) {
                engine.close();
            }
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            log.error("Error stopping Debezium engine", e);
        }
        running = false;
        log.info("Debezium CDC outbox publisher stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void processRecord(ChangeEvent<String, String> record) {
        if (record.value() == null) {
            return;
        }

        JsonNode root = objectMapper.readTree(record.value());
        String op = root.path("op").asText();

        // c = INSERT via WAL, r = snapshot READ at startup
        if (!"c".equals(op) && !"r".equals(op)) {
            return;
        }

        JsonNode after = root.path("after");
        if (after.isMissingNode() || after.isNull()) {
            return;
        }

        UUID id = UUID.fromString(after.get("id").asText());
        String eventType = after.get("event_type").asText();
        UUID aggregateId = UUID.fromString(after.get("aggregate_id").asText());

        JsonNode payloadNode = after.get("payload");
        String payload = payloadNode.isTextual() ? payloadNode.asText() : payloadNode.toString();

        EventEnvelope envelope = new EventEnvelope(
            id, EventType.valueOf(eventType), aggregateId, payload);

        rabbitTemplate.convertAndSend(RabbitMqSharedConfig.OUTBOX_EXCHANGE, eventType, envelope);
        log.info("CDC published: {} for aggregate {}", eventType, aggregateId);

        outboxRepository.deleteById(id);
    }

    private Properties buildProperties() {
        if (!(dataSource instanceof com.zaxxer.hikari.HikariDataSource hds)) {
            throw new IllegalStateException("Expected HikariDataSource but got " + dataSource.getClass());
        }
        URI uri = URI.create(hds.getJdbcUrl().substring(5));

        Properties props = new Properties();
        props.setProperty("name", "outbox-connector");
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");

        String offsetFile;
        try {
            offsetFile = Files.createTempFile("debezium-offsets-", ".dat").toString();
        } catch (IOException e) {
            offsetFile = "/tmp/checkout-pulse-debezium-offsets-" + ProcessHandle.current().pid() + ".dat";
        }
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", offsetFile);
        props.setProperty("offset.flush.interval.ms", "1000");

        props.setProperty("database.hostname", uri.getHost());
        props.setProperty("database.port", String.valueOf(uri.getPort()));
        props.setProperty("database.user", hds.getUsername());
        props.setProperty("database.password", hds.getPassword());
        props.setProperty("database.dbname", uri.getPath().substring(1));

        props.setProperty("topic.prefix", "checkout-pulse");
        props.setProperty("table.include.list", "shared.outbox");
        props.setProperty("slot.name", "outbox_slot");
        props.setProperty("plugin.name", "pgoutput");
        props.setProperty("publication.name", "outbox_publication");
        props.setProperty("publication.autocreate.mode", "filtered");

        props.setProperty("snapshot.mode", "initial");
        props.setProperty("converter.schemas.enable", "false");

        return props;
    }
}
