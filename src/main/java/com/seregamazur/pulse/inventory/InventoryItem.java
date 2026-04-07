package com.seregamazur.pulse.inventory;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Table(name = "inventory_items", schema = "inventory")
@Getter
public class InventoryItem {

    @Id
    private UUID productId;

    private long availableQuantity;

    @Version
    private Long version;

    protected InventoryItem() {
    }

    public InventoryItem(UUID productId, long initialQuantity) {
        this.productId = productId;
        this.availableQuantity = initialQuantity;
    }

    public void addStock(long amount) {
        this.availableQuantity += amount;
    }

    public void reserve(long quantityToReserve) {
        if (quantityToReserve <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.availableQuantity < quantityToReserve) {
            throw new OutOfStockException("Not enough stock for product " + productId);
        }
        this.availableQuantity -= quantityToReserve;
    }

    public void restock(long quantityToAdd) {
        this.availableQuantity += quantityToAdd;
    }
}