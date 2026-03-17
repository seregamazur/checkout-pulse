package com.seregamazur.pulse.order;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "order_items", schema = "orders")
@Data
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private BigDecimal price;

    public OrderItem() {
    }

    protected OrderItem(UUID productId, int quantity, BigDecimal price) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }

}

