package com.seregamazur.pulse.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.seregamazur.pulse.cart.dto.CartItemDetailed;
import com.seregamazur.pulse.cart.dto.CartResponse;
import com.seregamazur.pulse.inventory.Product;
import com.seregamazur.pulse.inventory.ProductRepository;
import com.seregamazur.pulse.order.dto.OrderResponse;
import com.seregamazur.pulse.order.idempotency.IdempotencyRecord;
import com.seregamazur.pulse.order.idempotency.IdempotencyRepository;
import com.seregamazur.pulse.order.idempotency.IdempotencyStatus;
import com.seregamazur.pulse.order.inbox.OrderInbox;
import com.seregamazur.pulse.order.inbox.OrderInboxRepository;
import com.seregamazur.pulse.shared.event.InventoryFailedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.event.PaymentCompletedEvent;
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
public class OrderService {

    private final IdempotencyRepository idempotencyRepository;
    private final OutboxRepository outboxRepository;
    private final OrderInboxRepository inboxRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper mapper;

    @Transactional
    public OrderResponse createOrder(CartResponse cart, UUID idempotencyKey) throws IllegalStateException {
        Optional<IdempotencyRecord> record = idempotencyRepository.findById(idempotencyKey);
        if (record.isPresent()) {
            if (record.get().getStatus() == IdempotencyStatus.COMPLETED) {
                OrderResponse orderResponse = mapper.readValue(record.get().getResponseBody(), OrderResponse.class);
                return orderResponse;
            } else {
                //TODO need some scheduler to get rid of such orders
                throw new IllegalStateException("Idempotency not finished progress!");
            }
        }

        List<UUID> productIds = cart.items().stream()
            .map(CartItemDetailed::productId)
            .toList();
        var products = productRepository.findAllById(productIds);

        Order order = Order.create(cart.userId());
        OrderCreatedEvent event;
        List<OrderCreatedEvent.Item> eventItems = new ArrayList<>();

        for (Product product : products) {
            for (CartItemDetailed item : cart.items()) {
                if (product.getId().compareTo(item.productId()) == 0) {
                    BigDecimal itemPrice = product.getBasePrice();
                    order.addItem(product.getId(), item.quantity(), itemPrice);
                    eventItems.add(new OrderCreatedEvent.Item(product.getId(), item.quantity()));
                }
            }
        }
        Order savedOrder = orderRepository.save(order);
        event = new OrderCreatedEvent(savedOrder.getId(), eventItems, savedOrder.getTotalAmount());
        idempotencyRepository.save(IdempotencyRecord.createCompleted(idempotencyKey,
            mapper.writeValueAsString(savedOrder)));

        OutboxRecord outboxRecord =
            new OutboxRecord(UUID.randomUUID(), OutboxType.ORDER, savedOrder.getId(),
                EventType.ORDER_CREATED, mapper.writeValueAsString(event));
        outboxRepository.save(outboxRecord);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event, UUID id) {
        Optional<Order> order = orderRepository.findById(event.orderId());
        if (order.isPresent()) {
            inboxRepository.save(new OrderInbox(id, Instant.now()));
            order.get().markAsPaid();
            orderRepository.save(order.get());
        } else {
            throw new IllegalStateException();
        }

    }

    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event, UUID id) {
        Optional<Order> order = orderRepository.findById(event.orderId());
        if (order.isPresent()) {
            inboxRepository.save(new OrderInbox(id, Instant.now()));
            order.get().cancel(event.reason());
            orderRepository.save(order.get());
        } else {
            throw new IllegalStateException();
        }
    }

    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event, UUID id) {
        Optional<Order> order = orderRepository.findById(event.orderId());
        if (order.isPresent()) {
            inboxRepository.save(new OrderInbox(id, Instant.now()));
            order.get().cancel(event.reason());
            orderRepository.save(order.get());
        } else {
            throw new IllegalStateException();
        }
    }

    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event, UUID id) {
        Optional<Order> order = orderRepository.findById(event.orderId());
        if (order.isPresent()) {
            inboxRepository.save(new OrderInbox(id, Instant.now()));
            order.get().markAsReservedForPostPayment();
            orderRepository.save(order.get());
        } else {
            throw new IllegalStateException();
        }
    }


}
