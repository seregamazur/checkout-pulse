package com.seregamazur.pulse.shared.event;

import java.util.List;
import java.util.UUID;

public record PaymentFailedEvent(UUID paymentId, UUID orderId, List<OrderCreatedEvent.Item> items, String provider, UUID providerPaymentId,
                                 String reason) {
}
