package com.seregamazur.pulse.order.views.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_views_inbox", schema = "orders")
@Data
public class OrderViewInbox {
    @Id
    private UUID id;
    private Instant processedAt;

    protected OrderViewInbox() {
    }

    public OrderViewInbox(UUID id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }
}
