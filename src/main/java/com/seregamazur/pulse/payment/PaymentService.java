package com.seregamazur.pulse.payment;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seregamazur.pulse.payment.inbox.PaymentInbox;
import com.seregamazur.pulse.payment.inbox.PaymentInboxRepository;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.PaymentFailedEvent;
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
        Payment payment = new Payment(
            UUID.randomUUID(),
            event.orderId(),
            event.totalPrice(),
            PaymentStatus.PENDING,
            "ADYEN",
            null,
            Instant.now(),
            null
        );

        paymentRepository.save(payment);

        boolean success = rand.nextBoolean();

//        if (success) {
//            payment.markPaid();
//            paymentRepository.save(payment);
//            outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.PAYMENT, event.orderId(),
//                EventType.PAYMENT_COMPLETED,
//                mapper.writeValueAsString(new PaymentCompletedEvent(
//                    payment.getId(), event.orderId(), event.totalPrice(), payment.getProvider(),
//                    payment.getProviderPaymentId(), payment.getPaidAt()))));
//        } else {
        payment.markFailed();
        paymentRepository.save(payment);
        outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.PAYMENT, event.orderId(),
            EventType.PAYMENT_FAILED,
            mapper.writeValueAsString(new PaymentFailedEvent(
                payment.getId(), event.orderId(), event.items(), payment.getProvider(),
                payment.getProviderPaymentId(), "NOT ENOUGH MONEY"))));
//        }

        inboxRepository.save(new PaymentInbox(id, Instant.now()));
    }
}
