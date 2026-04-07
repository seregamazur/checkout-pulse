package com.seregamazur.pulse.payment.inbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "payment_inbox", schema = "payment")
@Getter
public class PaymentInbox {
    @Id
    private UUID id;
    private Instant processedAt;

    protected PaymentInbox() {
    }

    public PaymentInbox(UUID id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }
}
