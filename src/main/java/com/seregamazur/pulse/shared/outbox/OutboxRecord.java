package com.seregamazur.pulse.shared.outbox;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "outbox", schema = "shared")
@Getter
public class OutboxRecord {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private OutboxType aggregateType;
    private UUID aggregateId;
    @Enumerated(EnumType.STRING)
    private EventType eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    public OutboxRecord() {
    }

    public OutboxRecord(UUID id, OutboxType aggregateType,
        UUID aggregateId, EventType eventType, String payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

}
