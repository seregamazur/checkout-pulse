package com.seregamazur.pulse.order.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_inbox", schema = "orders")
@Data
public class OrderInbox {
    @Id
    private UUID id;
    private Instant processedAt;

    protected OrderInbox() {
    }

    public OrderInbox(UUID id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }
}
