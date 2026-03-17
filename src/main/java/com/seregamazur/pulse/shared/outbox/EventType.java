package com.seregamazur.pulse.shared.outbox;

public enum EventType {
    ORDER_CREATED,
    ORDER_IN_PROGRESS,
    ORDER_COMPLETED,
    ORDER_CANCELLED,

    INVENTORY_RESERVED,
    INVENTORY_FAILED,

    PAYMENT_IN_PROGRESS,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED
}
