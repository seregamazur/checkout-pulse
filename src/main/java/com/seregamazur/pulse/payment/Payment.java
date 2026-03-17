package com.seregamazur.pulse.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "payments", schema = "payment")
@Data
class Payment {

    @Id
    private UUID id;

    private UUID orderId;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String provider;

    private UUID providerPaymentId;

    private Instant createdAt;

    private Instant paidAt;

    public Payment() {
    }

    public Payment(UUID id, UUID orderId, BigDecimal amount,
        PaymentStatus status, String provider, UUID providerPaymentId, Instant createdAt, Instant paidAt) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    public void markPaid() {
        this.status = PaymentStatus.AUTHORIZED;
        this.providerPaymentId = UUID.randomUUID();
        this.paidAt = Instant.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
        this.providerPaymentId = UUID.randomUUID();
        this.paidAt = Instant.now();
    }


}
