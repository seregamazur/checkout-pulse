package com.seregamazur.pulse.shared.event;

import java.util.UUID;

import com.seregamazur.pulse.shared.outbox.EventType;
import com.seregamazur.pulse.shared.outbox.OutboxRecord;

public record EventEnvelope(UUID id, EventType eventType, UUID aggregateId, String payload) {
    public static EventEnvelope fromOutboxRecord(OutboxRecord rec) {
        return new EventEnvelope(
            rec.getId(),
            rec.getEventType(),
            rec.getAggregateId(),
            rec.getPayload());
    }
}
