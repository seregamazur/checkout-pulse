package com.seregamazur.pulse.inventory.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "inventory_inbox", schema = "inventory")
@Getter
public class InventoryInbox {
    @Id
    private UUID id;
    private Instant processedAt;

    protected InventoryInbox() {
    }

    public InventoryInbox(UUID id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }
}
