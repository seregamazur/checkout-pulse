package com.seregamazur.pulse.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "orders", schema = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public static Order create(UUID userId) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.userId = userId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = BigDecimal.ZERO;
        order.createdAt = LocalDateTime.now();
        return order;
    }

    public void addItem(UUID productId, int quantity, BigDecimal priceAtCheckout) {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot add items to not PENDING order");
        }

        OrderItem newItem = new OrderItem(productId, quantity, priceAtCheckout);
        this.items.add(newItem);
        newItem.setOrder(this);
        this.recalculateTotal();
    }

    public void markAsReserved() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot reserve not PENDING order");
        }
        this.status = OrderStatus.RESERVED;
    }

    public void markAsPaid() {
        if (this.status != OrderStatus.RESERVED && this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("Invalid order state for payment: " + this.status);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}

