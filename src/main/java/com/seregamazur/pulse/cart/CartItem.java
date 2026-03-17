package com.seregamazur.pulse.cart;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "cart_items", schema = "inventory")
public class CartItem {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    private UUID productId;
    private int quantity;

    protected CartItem() {
    }

    protected CartItem(Cart cart, UUID productId, int quantity) {
        this.id = UUID.randomUUID();
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
    }

    protected void updateQuantity(int newQuantity) {
        if (newQuantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        this.quantity = newQuantity;
    }
}