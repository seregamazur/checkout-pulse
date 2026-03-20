package com.seregamazur.pulse.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentCompletedEvent(UUID paymentId, UUID orderId, List<OrderCreatedEvent.Item> items, BigDecimal totalPrice,
                                    String provider,
                                    UUID providerPaymentId, Instant paidAt) {
}
