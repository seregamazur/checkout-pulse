package com.seregamazur.pulse.order.views;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.seregamazur.pulse.order.Order;
import com.seregamazur.pulse.order.OrderRepository;
import com.seregamazur.pulse.order.views.inbox.OrderViewInbox;
import com.seregamazur.pulse.order.views.inbox.OrderViewInboxRepository;
import com.seregamazur.pulse.shared.InventoryStatus;
import com.seregamazur.pulse.shared.OrderStrategy;
import com.seregamazur.pulse.shared.PaymentStatus;
import com.seregamazur.pulse.shared.event.OrderUpdatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderProjectionHandler {

    private final OrderViewRepository viewRepository;
    private final OrderRepository orderRepository;
    private final OrderViewInboxRepository inboxRepository;

    public void onOrderUpdate(OrderUpdatedEvent event, UUID eventId) {
        Order order = orderRepository.findById(event.orderId()).orElseThrow();

        OrderView view = viewRepository.findById(order.getId()).orElse(new OrderView());

        view.setOrderId(order.getId());
        view.setDisplayStatus(calculateDisplayStatus(order));
        view.setLastUpdated(Instant.now());

        viewRepository.save(view);

        OrderViewInbox inbox = new OrderViewInbox(eventId, Instant.now());
        inboxRepository.save(inbox);
    }

    private String calculateDisplayStatus(Order order) {

        PaymentStatus payment = order.getPaymentStatus();
        InventoryStatus inventory = order.getInventoryStatus();
        OrderStrategy strategy = order.getStrategy();

        // --- PAYMENT FIRST
        if (payment == PaymentStatus.REFUNDED) {
            return "REFUNDED";
        }

        if (payment == PaymentStatus.DECLINED) {
            return "FAILED_PAYMENT";
        }

        // --- POST PAYMENT SPECIAL CASE ---

        if (strategy == OrderStrategy.POST_PAYMENT) {

            if (inventory == InventoryStatus.OUT_OF_STOCK) {
                if (payment == PaymentStatus.SUCCESS) {
                    return "REFUND_IN_PROGRESS";
                }
                return "FAILED_OUT_OF_STOCK";
            }

            if (payment == PaymentStatus.SUCCESS &&
                inventory == InventoryStatus.NOT_STARTED) {
                return "AWAITING_INVENTORY";
            }
        }

        // --- SUCCESS ---

        if (payment == PaymentStatus.SUCCESS &&
            inventory == InventoryStatus.RESERVED) {
            return "COMPLETED";
        }

        // --- INVENTORY FAIL

        if (inventory == InventoryStatus.OUT_OF_STOCK) {
            return "FAILED_OUT_OF_STOCK";
        }

        // --- IN PROGRESS ---

        if (payment == PaymentStatus.NOT_STARTED &&
            inventory == InventoryStatus.RESERVED) {
            return "AWAITING_PAYMENT";
        }

        if (payment == PaymentStatus.SUCCESS) {
            return "PROCESSING";
        }

        return "PROCESSING";
    }
}