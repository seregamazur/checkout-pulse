package com.seregamazur.pulse.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seregamazur.pulse.payment.inbox.PaymentInbox;
import com.seregamazur.pulse.payment.inbox.PaymentInboxRepository;
import com.seregamazur.pulse.shared.PaymentStatus;
import com.seregamazur.pulse.shared.event.InventoryFailedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.event.PaymentCompletedEvent;
import com.seregamazur.pulse.shared.event.PaymentRefundEvent;
import com.seregamazur.pulse.shared.outbox.EventType;
import com.seregamazur.pulse.shared.outbox.OutboxRecord;
import com.seregamazur.pulse.shared.outbox.OutboxRepository;
import com.seregamazur.pulse.shared.outbox.OutboxType;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentInboxRepository inboxRepository;
    private final Random rand = new Random();
    private final ObjectMapper mapper;

    @Transactional
    public void processPayment(InventoryReservedEvent event, UUID id) {
        Payment payment = new Payment(UUID.randomUUID(), event.orderId(), event.totalPrice(), PaymentStatus.PENDING, "ADYEN", null, Instant.now(), null);

        paymentRepository.save(payment);

        randomlyPayOrDecline(payment, event.orderId(), event.items(), event.totalPrice());

        inboxRepository.save(new PaymentInbox(id, Instant.now()));
    }

    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event, UUID id) {
        Optional<Payment> optionalPayment = paymentRepository.findByOrderId(event.orderId());

        if (optionalPayment.isPresent()) {
            Payment payment = optionalPayment.get();

            payment.recordAsRefund();
            paymentRepository.save(payment);
            outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.PAYMENT, event.orderId(), EventType.PAYMENT_REFUNDED, mapper.writeValueAsString(new PaymentRefundEvent(payment.getId(), event.orderId(), event.items(), payment.getProvider(), payment.getProviderPaymentId(), "No product at the moment"))));

            inboxRepository.save(new PaymentInbox(id, Instant.now()));
        }
    }

    @Transactional
    public void onOrderCreated(OrderCreatedEvent event, UUID id) {
        Payment payment = new Payment(UUID.randomUUID(), event.orderId(), event.totalPrice(), PaymentStatus.PENDING, "ADYEN", null, Instant.now(), null);

        paymentRepository.save(payment);

        randomlyPayOrDecline(payment, event.orderId(), event.items(), event.totalPrice());

        inboxRepository.save(new PaymentInbox(id, Instant.now()));
    }

    private void randomlyPayOrDecline(Payment payment, UUID event, List<OrderCreatedEvent.Item> items, BigDecimal paymentAmount) {
//        boolean success = rand.nextBoolean();

//        if (success) {
        payment.recordAsPaid();
        paymentRepository.save(payment);
        outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.PAYMENT, event, EventType.PAYMENT_PROCESSED, mapper.writeValueAsString(new PaymentCompletedEvent(payment.getId(), event, items, paymentAmount, payment.getProvider(), payment.getProviderPaymentId(), payment.getPaidAt()))));
//        } else {
//            payment.recordAsDeclined();
//            paymentRepository.save(payment);
//            outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.PAYMENT, event, EventType.PAYMENT_FAILED, mapper.writeValueAsString(new PaymentDeclinedEvent(payment.getId(), event, items, payment.getProvider(), payment.getProviderPaymentId(), "NOT ENOUGH MONEY"))));
//        }
    }
}
