package com.seregamazur.pulse.shared.outbox;

public enum EventType {
    ORDER_CREATED,
    ORDER_UPDATED,

    STOCK_RESERVED,
    STOCK_RELEASED,
    STOCK_FAILED,

    PAYMENT_PROCESSED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED
}
