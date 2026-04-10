package com.seregamazur.pulse.cart;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "cart_items", schema = "inventory")
public class CartItem {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    private UUID productId;
    private long quantity;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CartItem() {
    }

    protected CartItem(Cart cart, UUID productId, long quantity) {
        this.id = UUID.randomUUID();
        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
    }

    protected void updateQuantity(long newQuantity) {
        if (newQuantity < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        this.quantity = newQuantity;
    }
}