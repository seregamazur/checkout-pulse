package com.seregamazur.pulse.infra;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.seregamazur.pulse.cart.Cart;
import com.seregamazur.pulse.cart.CartRepository;
import com.seregamazur.pulse.infra.inbox.RedisStockInbox;
import com.seregamazur.pulse.infra.inbox.RedisStockInboxRepository;
import com.seregamazur.pulse.inventory.InventoryItem;
import com.seregamazur.pulse.inventory.InventoryRepository;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;

import jakarta.transaction.Transactional;

@Component
public class RedisStockProvider {

    private final RedisTemplate<String, Long> redis;
    private final RedisTemplate<String, Object> cartRedis;
    private final InventoryRepository inventoryRepository;
    private final CartRepository cartRepository;
    private final RedisStockInboxRepository inboxRepository;

    public RedisStockProvider(
        @Qualifier("redisStock") RedisTemplate<String, Long> redis,
        @Qualifier("redisCart") RedisTemplate<String, Object> cartRedis,
        InventoryRepository inventoryRepository,
        CartRepository cartRepository,
        RedisStockInboxRepository inboxRepository) {
        this.redis = redis;
        this.cartRedis = cartRedis;
        this.inventoryRepository = inventoryRepository;
        this.cartRepository = cartRepository;
        this.inboxRepository = inboxRepository;
    }

    public long getAvailableStock(UUID productId) {
        Long stock = redis.opsForValue().get("stock:" + productId);

        if (stock == null) {
            InventoryItem item = inventoryRepository.findById(productId).orElseThrow(() -> new RuntimeException("Unexpected exception! Failed to find product in DB"));
            stock = item.getAvailableQuantity();
            redis.opsForValue().setIfAbsent("stock:" + productId, stock);
        }
        return Math.toIntExact(stock);
    }

    @Transactional
    public void updateRedisStock(List<OrderCreatedEvent.Item> items, UUID eventId) {
        List<InventoryItem> stockBeforeUpdate = new ArrayList<>();
        List<InventoryItem> inventoryItems
            = inventoryRepository.findAllById(items.stream().map(OrderCreatedEvent.Item::productId).toList());
        try {
            for (InventoryItem item : inventoryItems) {
                stockBeforeUpdate.add(item);
                redis.opsForValue().set("stock:" + item.getProductId(), item.getAvailableQuantity());
            }
            inboxRepository.save(new RedisStockInbox(eventId, Instant.now()));
        } catch (Exception e) {
            if (!stockBeforeUpdate.isEmpty()) {
                for (InventoryItem item : stockBeforeUpdate) {
                    redis.opsForValue().set("stock:" + item.getProductId(), item.getAvailableQuantity());
                }
            }
        }
    }

    @Transactional
    public void cleanRedisAndDbCartAndReserved(OrderCreatedEvent event, UUID eventId) {
        String cartKey = "cart:" + event.userId();

        Map<Object, Object> cartBackup = cartRedis.opsForHash().entries(cartKey);

        try {
            for (OrderCreatedEvent.Item item : event.items()) {
                cartRedis.opsForValue().decrement("reserved:" + item.productId(), item.quantity());
            }

            cartRedis.delete(cartKey);
            Cart cartFromDb = cartRepository.findByUserId(event.userId()).orElseThrow(() -> new RuntimeException("Cart Not Found"));
            cartFromDb.clear();
            cartRepository.save(cartFromDb);
            inboxRepository.save(new RedisStockInbox(eventId, Instant.now()));

        } catch (Exception e) {
            for (OrderCreatedEvent.Item item : event.items()) {
                cartRedis.opsForValue().increment("reserved:" + item.productId(), item.quantity());
            }

            if (cartBackup != null && !cartBackup.isEmpty()) {
                cartRedis.opsForHash().putAll(cartKey, cartBackup);
                cartRedis.expire(cartKey, Duration.ofMinutes(5));
            }

            throw new RuntimeException("Failed to finalize order in Redis/DB, rolled back changes", e);
        }
    }

    public Cart getUsersCart(UUID cartId) {
        Optional<Cart> cart = cartRepository.findById(cartId);
        if (cart.isPresent()) {
            return cart.get();
        }
        throw new IllegalArgumentException("No cart with id:" + cartId.toString());
    }

    //TODO explain in doc why it is really bad idea (cache stampede)//
    //we need to change value, not invalidate cache
    public void invalidateStockCache(UUID productId) {
        if (redis.hasKey("stock:" + productId)) {
            redis.delete("stock:" + productId);
        }
    }


}