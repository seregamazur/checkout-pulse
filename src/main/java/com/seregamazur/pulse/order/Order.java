package com.seregamazur.pulse.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.seregamazur.pulse.shared.InventoryStatus;
import com.seregamazur.pulse.shared.OrderStrategy;
import com.seregamazur.pulse.shared.PaymentStatus;

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

    @Enumerated(EnumType.STRING)
    private OrderFailureReason failureReason;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private InventoryStatus inventoryStatus;

    @Enumerated(EnumType.STRING)
    private OrderStrategy strategy;

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
        order.status = OrderStatus.CREATED;
        order.paymentStatus = PaymentStatus.NOT_STARTED;
        order.inventoryStatus = InventoryStatus.NOT_STARTED;
        order.totalAmount = BigDecimal.ZERO;
        order.createdAt = LocalDateTime.now();
        return order;
    }

    public void addItem(UUID productId, int quantity, BigDecimal priceAtCheckout) {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("Cannot add items to not PENDING order");
        }

        OrderItem newItem = new OrderItem(productId, quantity, priceAtCheckout);
        this.items.add(newItem);
        newItem.setOrder(this);
        this.recalculateTotal();
    }

    public void recordPaymentSuccess() {
        this.paymentStatus = PaymentStatus.SUCCESS;
        this.status = OrderStatus.PROCESSING;
        checkIfFinished();
    }

    public void recordInventoryReserved() {
        this.inventoryStatus = InventoryStatus.RESERVED;
        checkIfFinished();
    }

    public void recordInventoryReleased() {
        this.inventoryStatus = InventoryStatus.RELEASED;
    }

    public void recordPaymentRefund() {
        this.paymentStatus = PaymentStatus.REFUNDED;
    }

    public void recordPaymentDeclined() {
        this.paymentStatus = PaymentStatus.DECLINED;
        markAsFailed(OrderFailureReason.PAYMENT_FAILED);
    }

    public void recordInventoryFailed() {
        this.inventoryStatus = InventoryStatus.OUT_OF_STOCK;
        markAsFailed(OrderFailureReason.OUT_OF_STOCK);
    }

    public void markAsFailed(OrderFailureReason failureReason) {
        this.status = OrderStatus.FAILED;
        this.failureReason = failureReason;
    }

    private void checkIfFinished() {
        if (this.paymentStatus == PaymentStatus.SUCCESS && this.inventoryStatus == InventoryStatus.RESERVED) {
            this.status = OrderStatus.COMPLETED;
        }
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}

