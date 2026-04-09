package com.seregamazur.pulse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.seregamazur.pulse.cart.Cart;
import com.seregamazur.pulse.inventory.InventoryItem;
import com.seregamazur.pulse.inventory.OutOfStockException;
import com.seregamazur.pulse.inventory.Product;
import com.seregamazur.pulse.order.Order;
import com.seregamazur.pulse.order.OrderFailureReason;
import com.seregamazur.pulse.order.OrderStatus;
import com.seregamazur.pulse.order.dto.OrderResponse;
import com.seregamazur.pulse.shared.InventoryStatus;
import com.seregamazur.pulse.shared.PaymentStatus;
import com.seregamazur.pulse.shared.event.EventEnvelope;
import com.seregamazur.pulse.shared.event.InventoryFailedEvent;
import com.seregamazur.pulse.shared.event.InventoryReleasedEvent;
import com.seregamazur.pulse.shared.event.InventoryReservedEvent;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.event.OrderUpdatedEvent;
import com.seregamazur.pulse.shared.event.PaymentCompletedEvent;
import com.seregamazur.pulse.shared.event.PaymentDeclinedEvent;
import com.seregamazur.pulse.shared.event.PaymentRefundEvent;
import com.seregamazur.pulse.shared.outbox.DebeziumOutboxPublisher;
import com.seregamazur.pulse.shared.outbox.EventType;
import com.seregamazur.pulse.shared.outbox.OutboxRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderSagaIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    DebeziumOutboxPublisher debeziumOutboxPublisher;

    // ── Order Creation ───────────────────────────────────────────────

    @Nested
    @DisplayName("Order Creation")
    class OrderCreation {

        @Test
        @DisplayName("Creates order from cart with correct state and outbox record")
        void createOrder_createsOrderAndOutboxRecord() {
            Product product = createProduct("iPhone", new BigDecimal("999.00"), 50);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 2);

            OrderResponse response = orderService.createOrder(cart.getId(), UUID.randomUUID());

            assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("1998.00"));

            Order order = orderRepository.findById(response.orderId()).orElseThrow();
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_STARTED);
            assertThat(order.getInventoryStatus()).isEqualTo(InventoryStatus.NOT_STARTED);

            int itemCount = inTransaction(() ->
                orderRepository.findById(response.orderId()).orElseThrow().getItems().size());
            assertThat(itemCount).isEqualTo(1);

            List<OutboxRecord> outbox = outboxRepository.findAll();
            assertThat(outbox).anyMatch(r -> r.getEventType() == EventType.ORDER_CREATED);
            assertThat(outbox).anyMatch(r -> r.getEventType() == EventType.ORDER_UPDATED);
        }

        @Test
        @DisplayName("Idempotency key prevents duplicate orders — returns cached response")
        void createOrder_idempotencyKey_returnsCachedResponse() {
            Product product = createProduct("AirPods", new BigDecimal("249.00"), 100);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            UUID idempotencyKey = UUID.randomUUID();

            OrderResponse first = orderService.createOrder(cart.getId(), idempotencyKey);
            OrderResponse second = orderService.createOrder(cart.getId(), idempotencyKey);

            assertThat(second.status()).isEqualTo(first.status());
            assertThat(second.totalAmount()).isEqualByComparingTo(first.totalAmount());
            assertThat(orderRepository.count()).isEqualTo(1);
            assertThat(idempotencyRepository.count()).isEqualTo(1);
        }
    }

    // ── Optimistic Strategy (Inventory → Payment → Complete) ─────────

    @Nested
    @DisplayName("Optimistic Strategy Saga")
    class OptimisticSaga {

        @Test
        @DisplayName("Happy path: inventory reserved → payment success → order COMPLETED")
        void happyPath_orderCompleted() {
            Product product = createProduct("MacBook", new BigDecimal("1299.00"), 15);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 1);

            // Step 1: Inventory reserves stock
            OrderCreatedEvent orderEvent = new OrderCreatedEvent(
                USER_ID, orderId, items, created.totalAmount());
            inventoryService.onOrderCreated(orderEvent, UUID.randomUUID());

            InventoryItem inv = inventoryRepository.findById(product.getId()).orElseThrow();
            assertThat(inv.getAvailableQuantity()).isEqualTo(14);
            assertThat(outboxRepository.findAll())
                .anyMatch(r -> r.getEventType() == EventType.STOCK_RESERVED);

            // Step 2: Order learns about successful reservation
            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                orderId, items, created.totalAmount());
            orderService.onInventoryReserved(reservedEvent, UUID.randomUUID());

            Order afterReserve = orderRepository.findById(orderId).orElseThrow();
            assertThat(afterReserve.getInventoryStatus()).isEqualTo(InventoryStatus.RESERVED);
            assertThat(afterReserve.getStatus()).isNotEqualTo(OrderStatus.COMPLETED);

            // Step 3: Payment processes successfully
            paymentService.processPayment(reservedEvent, UUID.randomUUID());
            assertThat(outboxRepository.findAll())
                .anyMatch(r -> r.getEventType() == EventType.PAYMENT_PROCESSED);

            // Step 4: Order learns about successful payment → COMPLETED
            PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
                UUID.randomUUID(), orderId, items, created.totalAmount(),
                "ADYEN", UUID.randomUUID(), Instant.now());
            orderService.onPaymentCompleted(paymentEvent, UUID.randomUUID());

            Order finalOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(finalOrder.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(finalOrder.getInventoryStatus()).isEqualTo(InventoryStatus.RESERVED);

            assertThat(orderInboxRepository.count()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Out of stock: inventory fails → order FAILED with OUT_OF_STOCK")
        void outOfStock_orderFailed() {
            Product product = createProduct("Limited Edition", new BigDecimal("500.00"), 1);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 2);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 2);

            OrderCreatedEvent orderEvent = new OrderCreatedEvent(
                USER_ID, orderId, items, created.totalAmount());

            assertThatThrownBy(() ->
                inventoryService.onOrderCreated(orderEvent, UUID.randomUUID())
            ).isInstanceOf(OutOfStockException.class);

            InventoryItem inv = inventoryRepository.findById(product.getId()).orElseThrow();
            assertThat(inv.getAvailableQuantity()).isEqualTo(1);

            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), EventType.ORDER_CREATED, orderId,
                objectMapper.writeValueAsString(orderEvent));
            inventoryService.publishFailure(envelope, new OutOfStockException("Not enough stock"));

            assertThat(outboxRepository.findAll())
                .anyMatch(r -> r.getEventType() == EventType.STOCK_FAILED);

            InventoryFailedEvent failedEvent = new InventoryFailedEvent(
                orderId, items, "Not enough stock");
            orderService.onInventoryFailed(failedEvent, UUID.randomUUID());

            Order failedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(failedOrder.getFailureReason()).isEqualTo(OrderFailureReason.OUT_OF_STOCK);
            assertThat(failedOrder.getInventoryStatus()).isEqualTo(InventoryStatus.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("Payment declined after reservation → inventory released, order FAILED")
        void paymentDeclined_inventoryReleasedOrderFailed() {
            Product product = createProduct("MacBook", new BigDecimal("1299.00"), 15);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 1);

            OrderCreatedEvent orderEvent = new OrderCreatedEvent(
                USER_ID, orderId, items, created.totalAmount());
            inventoryService.onOrderCreated(orderEvent, UUID.randomUUID());

            assertThat(inventoryRepository.findById(product.getId()).orElseThrow()
                .getAvailableQuantity()).isEqualTo(14);

            // Simulate payment declined
            PaymentDeclinedEvent declinedEvent = new PaymentDeclinedEvent(
                UUID.randomUUID(), orderId, items, "ADYEN", UUID.randomUUID(), "Insufficient funds");
            orderService.onPaymentDeclined(declinedEvent, UUID.randomUUID());

            Order afterDecline = orderRepository.findById(orderId).orElseThrow();
            assertThat(afterDecline.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(afterDecline.getPaymentStatus()).isEqualTo(PaymentStatus.DECLINED);
            assertThat(afterDecline.getFailureReason()).isEqualTo(OrderFailureReason.PAYMENT_FAILED);

            // Compensation: inventory is released
            inventoryService.onPaymentFailed(declinedEvent, UUID.randomUUID());

            InventoryItem inv = inventoryRepository.findById(product.getId()).orElseThrow();
            assertThat(inv.getAvailableQuantity()).isEqualTo(15);

            assertThat(outboxRepository.findAll())
                .anyMatch(r -> r.getEventType() == EventType.STOCK_RELEASED);

            InventoryReleasedEvent releasedEvent = new InventoryReleasedEvent(orderId, items);
            orderService.onInventoryReleased(releasedEvent, UUID.randomUUID());

            Order finalOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(finalOrder.getInventoryStatus()).isEqualTo(InventoryStatus.RELEASED);
        }
    }

    // ── Post-Payment Strategy (Payment → Inventory → Complete) ───────

    @Nested
    @DisplayName("Post-Payment Strategy Saga")
    class PostPaymentSaga {

        @Test
        @DisplayName("Happy path: payment first → inventory reserved → order COMPLETED")
        void happyPath_orderCompleted() {
            Product product = createProduct("iPad", new BigDecimal("799.00"), 30);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 1);

            // Step 1: Payment processes first (post-payment strategy)
            OrderCreatedEvent orderEvent = new OrderCreatedEvent(
                USER_ID, orderId, items, created.totalAmount());
            paymentService.onOrderCreated(orderEvent, UUID.randomUUID());

            assertThat(outboxRepository.findAll())
                .anyMatch(r -> r.getEventType() == EventType.PAYMENT_PROCESSED);

            // Step 2: Order learns about payment
            PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
                UUID.randomUUID(), orderId, items, created.totalAmount(),
                "ADYEN", UUID.randomUUID(), Instant.now());
            orderService.onPaymentCompleted(paymentEvent, UUID.randomUUID());

            Order afterPayment = orderRepository.findById(orderId).orElseThrow();
            assertThat(afterPayment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(afterPayment.getStatus()).isEqualTo(OrderStatus.PROCESSING);

            // Step 3: Inventory reserves stock after payment
            inventoryService.onPaymentCompleted(paymentEvent, UUID.randomUUID());

            InventoryItem inv = inventoryRepository.findById(product.getId()).orElseThrow();
            assertThat(inv.getAvailableQuantity()).isEqualTo(29);

            // Step 4: Order learns about inventory → COMPLETED
            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                orderId, items, created.totalAmount());
            orderService.onInventoryReserved(reservedEvent, UUID.randomUUID());

            Order finalOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(finalOrder.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(finalOrder.getInventoryStatus()).isEqualTo(InventoryStatus.RESERVED);
        }

        @Test
        @DisplayName("Inventory fails after payment → payment refunded, order FAILED")
        void inventoryFailsAfterPayment_refundTriggered() {
            Product product = createProduct("Rare Item", new BigDecimal("999.00"), 0);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 1);

            // Payment processes first
            OrderCreatedEvent orderEvent = new OrderCreatedEvent(
                USER_ID, orderId, items, created.totalAmount());
            paymentService.onOrderCreated(orderEvent, UUID.randomUUID());

            PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
                UUID.randomUUID(), orderId, items, created.totalAmount(),
                "ADYEN", UUID.randomUUID(), Instant.now());
            orderService.onPaymentCompleted(paymentEvent, UUID.randomUUID());

            // Inventory fails — out of stock
            assertThatThrownBy(() ->
                inventoryService.onPaymentCompleted(paymentEvent, UUID.randomUUID())
            ).isInstanceOf(OutOfStockException.class);

            // Publish inventory failure
            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(), EventType.PAYMENT_PROCESSED, orderId,
                objectMapper.writeValueAsString(paymentEvent));
            inventoryService.publishFailure(envelope, new OutOfStockException("Not enough stock"));

            // Payment gets refunded
            InventoryFailedEvent failedEvent = new InventoryFailedEvent(orderId, items, "Not enough stock");
            paymentService.onInventoryFailed(failedEvent, UUID.randomUUID());

            assertThat(outboxRepository.findAll())
                .anyMatch(r -> r.getEventType() == EventType.PAYMENT_REFUNDED);

            // Order learns about inventory failure
            orderService.onInventoryFailed(failedEvent, UUID.randomUUID());

            Order failedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(failedOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(failedOrder.getFailureReason()).isEqualTo(OrderFailureReason.OUT_OF_STOCK);

            // Order learns about refund
            PaymentRefundEvent refundEvent = new PaymentRefundEvent(
                UUID.randomUUID(), orderId, items, "ADYEN", UUID.randomUUID(), "No product at the moment");
            orderService.onPaymentRefund(refundEvent, UUID.randomUUID());

            Order refundedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(refundedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }
    }

    // ── Inbox Deduplication ──────────────────────────────────────────

    @Nested
    @DisplayName("Inbox Deduplication")
    class InboxDedup {

        @Test
        @DisplayName("Same event processed via listener twice — second call is no-op")
        void sameEventProcessedOnce() {
            Product product = createProduct("Watch", new BigDecimal("399.00"), 50);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 1);

            UUID eventId = UUID.randomUUID();
            PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
                UUID.randomUUID(), orderId, items, created.totalAmount(),
                "ADYEN", UUID.randomUUID(), Instant.now());

            orderService.onPaymentCompleted(paymentEvent, eventId);
            long inboxCountAfterFirst = orderInboxRepository.count();

            EventEnvelope envelope = new EventEnvelope(
                eventId, EventType.PAYMENT_PROCESSED, orderId,
                objectMapper.writeValueAsString(paymentEvent));
            orderQueueListener.processEvent(envelope);

            assertThat(orderInboxRepository.count()).isEqualTo(inboxCountAfterFirst);

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
    }

    // ── Order View Projection ────────────────────────────────────────

    @Nested
    @DisplayName("Order View Projection")
    class ViewProjection {

        @Test
        @DisplayName("Completed order produces PROCESSING display status")
        void completedOrder_correctDisplayStatus() {
            Product product = createProduct("Speaker", new BigDecimal("199.00"), 40);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();

            OrderUpdatedEvent updateEvent = new OrderUpdatedEvent(orderId);
            orderProjectionHandler.onOrderUpdate(updateEvent, UUID.randomUUID());

            var view = orderViewRepository.findById(orderId).orElseThrow();
            assertThat(view.getDisplayStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("Completed saga produces COMPLETED display status")
        void completedSaga_completedDisplayStatus() {
            Product product = createProduct("Tablet", new BigDecimal("499.00"), 20);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();
            List<OrderCreatedEvent.Item> items = eventItems(product.getId(), 1);

            InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                orderId, items, created.totalAmount());
            orderService.onInventoryReserved(reservedEvent, UUID.randomUUID());

            PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(
                UUID.randomUUID(), orderId, items, created.totalAmount(),
                "ADYEN", UUID.randomUUID(), Instant.now());
            orderService.onPaymentCompleted(paymentEvent, UUID.randomUUID());

            orderProjectionHandler.onOrderUpdate(new OrderUpdatedEvent(orderId), UUID.randomUUID());

            var view = orderViewRepository.findById(orderId).orElseThrow();
            assertThat(view.getDisplayStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("Failed order (out of stock) produces FAILED_OUT_OF_STOCK display status")
        void failedOutOfStock_correctDisplayStatus() {
            Product product = createProduct("Rare Item", new BigDecimal("999.00"), 50);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();

            InventoryFailedEvent failedEvent = new InventoryFailedEvent(
                orderId, eventItems(product.getId(), 1), "Not enough stock");
            orderService.onInventoryFailed(failedEvent, UUID.randomUUID());

            orderProjectionHandler.onOrderUpdate(new OrderUpdatedEvent(orderId), UUID.randomUUID());

            var view = orderViewRepository.findById(orderId).orElseThrow();
            assertThat(view.getDisplayStatus()).isEqualTo("FAILED_OUT_OF_STOCK");
        }

        @Test
        @DisplayName("Payment declined produces FAILED_PAYMENT display status")
        void paymentDeclined_correctDisplayStatus() {
            Product product = createProduct("Gadget", new BigDecimal("299.00"), 50);
            Cart cart = createCartWithItem(USER_ID, product.getId(), 1);
            OrderResponse created = orderService.createOrder(cart.getId(), UUID.randomUUID());
            UUID orderId = created.orderId();

            PaymentDeclinedEvent declinedEvent = new PaymentDeclinedEvent(
                UUID.randomUUID(), orderId, eventItems(product.getId(), 1),
                "ADYEN", UUID.randomUUID(), "Insufficient funds");
            orderService.onPaymentDeclined(declinedEvent, UUID.randomUUID());

            orderProjectionHandler.onOrderUpdate(new OrderUpdatedEvent(orderId), UUID.randomUUID());

            var view = orderViewRepository.findById(orderId).orElseThrow();
            assertThat(view.getDisplayStatus()).isEqualTo("FAILED_PAYMENT");
        }
    }
}
