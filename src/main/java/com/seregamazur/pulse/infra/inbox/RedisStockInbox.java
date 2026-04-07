package com.seregamazur.pulse.infra.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "redis_stock_inbox", schema = "infra")
public class RedisStockInbox {
    @Id
    private UUID id;
    private Instant processedAt;

    protected RedisStockInbox() {
    }

    public RedisStockInbox(UUID id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }
}
