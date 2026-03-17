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

    private int availableQuantity;

    @Version
    private Long version;

    protected InventoryItem() {
    }

    public InventoryItem(UUID productId, int initialQuantity) {
        this.productId = productId;
        this.availableQuantity = initialQuantity;
    }

    public void addStock(int amount) {
        this.availableQuantity += amount;
    }

    public void reserve(int quantityToReserve) {
        if (quantityToReserve <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.availableQuantity < quantityToReserve) {
            throw new OutOfStockException("Not enough stock for product " + productId);
        }
        this.availableQuantity -= quantityToReserve;
    }

    public void restock(int quantityToAdd) {
        this.availableQuantity += quantityToAdd;
    }
}