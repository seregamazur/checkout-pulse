package com.seregamazur.pulse.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.seregamazur.pulse.inventory.inbox.InventoryInbox;
import com.seregamazur.pulse.inventory.inbox.InventoryInboxRepository;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.InventoryFailedEvent;
import com.seregamazur.pulse.shared.event.InventoryReleasedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.event.PaymentCompletedEvent;
import com.seregamazur.pulse.shared.event.PaymentDeclinedEvent;
import com.seregamazur.pulse.shared.outbox.EventType;
import com.seregamazur.pulse.shared.outbox.OutboxRecord;
import com.seregamazur.pulse.shared.outbox.OutboxRepository;
import com.seregamazur.pulse.shared.outbox.OutboxType;

import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryInboxRepository inboxRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper mapper;

    @Retryable(
        value = OptimisticLockException.class,
        //1 + 2
        maxRetries = 2,
        delay = 100,
        timeUnit = TimeUnit.MILLISECONDS
    )
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event, UUID eventUUID) {
        List<UUID> products = event.items().stream().map(OrderCreatedEvent.Item::productId).toList();
        List<InventoryItem> inventoryItems = inventoryRepository.findAllById(products);

        for (OrderCreatedEvent.Item orderItem : event.items()) {
            for (InventoryItem inventoryItem : inventoryItems) {
                if (inventoryItem.getProductId().compareTo(orderItem.productId()) == 0) {
                    inventoryItem.reserve(orderItem.quantity());
                }
            }
        }
        inventoryRepository.saveAll(inventoryItems);
        InventoryInbox inbox = new InventoryInbox(eventUUID, Instant.now());
        inboxRepository.save(inbox);
        outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.INVENTORY, event.orderId(),
            EventType.STOCK_RESERVED,
            mapper.writeValueAsString(new InventoryReservedEvent(event.orderId(), event.items(), event.totalPrice()))));
    }

    @Retryable(
        value = OptimisticLockException.class,
        //1 + 2
        maxRetries = 2,
        delay = 100,
        timeUnit = TimeUnit.MILLISECONDS
    )
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event, UUID eventUUID) {
        List<UUID> products = event.items().stream().map(OrderCreatedEvent.Item::productId).toList();
        List<InventoryItem> inventoryItems = inventoryRepository.findAllById(products);

        for (OrderCreatedEvent.Item orderItem : event.items()) {
            for (InventoryItem inventoryItem : inventoryItems) {
                if (inventoryItem.getProductId().compareTo(orderItem.productId()) == 0) {
                    inventoryItem.reserve(orderItem.quantity());
                }
            }
        }
        inventoryRepository.saveAll(inventoryItems);
        InventoryInbox inbox = new InventoryInbox(eventUUID, Instant.now());
        inboxRepository.save(inbox);
        outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.INVENTORY, event.orderId(),
            EventType.STOCK_RESERVED,
            mapper.writeValueAsString(new InventoryReservedEvent(event.orderId(), event.items(), event.totalPrice()))));
    }

    @Retryable(
        value = OptimisticLockException.class,
        //1 + 2
        maxRetries = 2,
        delay = 100,
        timeUnit = TimeUnit.MILLISECONDS
    )
    @Transactional
    public void onPaymentFailed(PaymentDeclinedEvent event, UUID eventUUID) {
        List<UUID> products = event.items().stream().map(OrderCreatedEvent.Item::productId).toList();
        List<InventoryItem> inventoryItems = inventoryRepository.findAllById(products);

        for (OrderCreatedEvent.Item orderItem : event.items()) {
            for (InventoryItem inventoryItem : inventoryItems) {
                if (inventoryItem.getProductId().compareTo(orderItem.productId()) == 0) {
                    inventoryItem.restock(orderItem.quantity());
                }
            }
        }
        inventoryRepository.saveAll(inventoryItems);
        InventoryInbox inbox = new InventoryInbox(eventUUID, Instant.now());
        inboxRepository.save(inbox);

        outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.INVENTORY, event.orderId(),
            EventType.STOCK_RELEASED,
            mapper.writeValueAsString(new InventoryReleasedEvent(event.orderId(), event.items()))));
    }

    @Transactional
    public void publishFailure(EventEnvelope envelope, OutOfStockException e) {
        InventoryInbox inbox = new InventoryInbox(envelope.aggregateId(), Instant.now());
        inboxRepository.save(inbox);
        //TODO items here from order
        outboxRepository.save(new OutboxRecord(UUID.randomUUID(), OutboxType.INVENTORY, envelope.aggregateId(),
            EventType.STOCK_FAILED,
            mapper.writeValueAsString(new InventoryFailedEvent(envelope.aggregateId(),
                List.of(), e.getLocalizedMessage()))));
    }


}
