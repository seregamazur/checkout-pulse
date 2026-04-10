package com.seregamazur.pulse.infra;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.seregamazur.pulse.cart.Cart;
import com.seregamazur.pulse.cart.CartItemRepository;
import com.seregamazur.pulse.cart.CartRepository;
import com.seregamazur.pulse.inventory.InventoryItem;
import com.seregamazur.pulse.inventory.InventoryRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodic reconciliation of Redis cache against PostgreSQL (source of truth).
 * <p>
 * Uses a timestamp-based delta approach: only rows modified since the last run
 * are checked, and a cooldown window skips rows changed in the last N seconds
 * to avoid overwriting Redis with data from in-flight DB transactions.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationService {

    private static final String STOCK_PREFIX = "stock:";
    private static final String RESERVED_PREFIX = "reserved:";
    private static final String CART_PREFIX = "cart:";
    private static final String CLEANUP_QUEUE = "cart:cleanup:queue";
    private static final Duration COOLDOWN = Duration.ofSeconds(10);

    private final RedisTemplate<String, Long> redisStock;
    private final RedisTemplate<String, Object> redisCart;
    private final InventoryRepository inventoryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    private volatile Instant lastRunAt = Instant.EPOCH;

    public ReconciliationService(
        @Qualifier("redisStock") RedisTemplate<String, Long> redisStock,
        @Qualifier("redisCart") RedisTemplate<String, Object> redisCart,
        InventoryRepository inventoryRepository,
        CartRepository cartRepository,
        CartItemRepository cartItemRepository) {
        this.redisStock = redisStock;
        this.redisCart = redisCart;
        this.inventoryRepository = inventoryRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @Scheduled(fixedRateString = "${reconciliation.interval-ms:300000}")
    public void reconcile() {
        Instant coldBefore = Instant.now().minus(COOLDOWN);
        Instant since = lastRunAt;

        log.info("Starting delta reconciliation (since={}, before={})", since, coldBefore);
        int stockFixes = reconcileStock(since, coldBefore);
        int reservedFixes = reconcileReserved(since, coldBefore);
        int cartFixes = reconcileCarts(since, coldBefore);

        lastRunAt = coldBefore;
        log.info("Reconciliation complete: {} stock, {} reserved, {} cart fixes",
            stockFixes, reservedFixes, cartFixes);
    }

    public int reconcileStock(Instant since, Instant before) {
        List<InventoryItem> items = inventoryRepository.findModifiedBetween(since, before);
        int fixes = 0;

        for (InventoryItem item : items) {
            String key = STOCK_PREFIX + item.getProductId();
            Long redisValue = redisStock.opsForValue().get(key);
            long dbValue = item.getAvailableQuantity();

            if (redisValue == null || redisValue != dbValue) {
                redisStock.opsForValue().set(key, dbValue);
                log.warn("RECONCILE stock:{} — Redis was {}, DB is {}, corrected",
                    item.getProductId(), redisValue, dbValue);
                fixes++;
            }
        }
        return fixes;
    }

    public int reconcileReserved(Instant since, Instant before) {
        List<Object[]> rows = cartItemRepository.sumQuantityByProductIdModifiedBetween(since, before);
        int fixes = 0;

        for (Object[] row : rows) {
            UUID productId = (UUID) row[0];
            long expectedValue = ((Number) row[1]).longValue();
            String key = RESERVED_PREFIX + productId;
            Long redisValue = redisStock.opsForValue().get(key);

            if (redisValue == null || redisValue != expectedValue) {
                redisStock.opsForValue().set(key, expectedValue);
                log.warn("RECONCILE reserved:{} — Redis was {}, expected {} (from DB carts), corrected",
                    productId, redisValue, expectedValue);
                fixes++;
            }
        }

        return fixes;
    }

    public int reconcileCarts(Instant since, Instant before) {
        List<Cart> carts = cartRepository.findWithItemsModifiedBetween(since, before);
        int fixes = 0;

        for (Cart cart : carts) {
            String cartKey = CART_PREFIX + cart.getUserId();

            Map<String, Long> dbItems = new HashMap<>();
            cart.getItems().forEach(item ->
                dbItems.put(item.getProductId().toString(), item.getQuantity()));

            Map<Object, Object> redisEntries = redisCart.opsForHash().entries(cartKey);

            Map<String, Long> redisItems = new HashMap<>();
            redisEntries.forEach((k, v) ->
                redisItems.put(k.toString(), Long.valueOf(v.toString())));

            if (!dbItems.equals(redisItems)) {
                redisCart.delete(cartKey);
                if (!dbItems.isEmpty()) {
                    Map<String, Object> toWrite = new HashMap<>();
                    dbItems.forEach((k, v) -> toWrite.put(k, v));
                    redisCart.opsForHash().putAll(cartKey, toWrite);
                    redisCart.expire(cartKey, Duration.ofHours(1));
                }

                log.warn("RECONCILE cart:{} — Redis had {} entries, DB has {}, corrected",
                    cart.getUserId(), redisItems.size(), dbItems.size());
                fixes++;
            }

            if (!dbItems.isEmpty()) {
                Double score = redisCart.opsForZSet().score(CLEANUP_QUEUE, cart.getUserId());
                if (score == null) {
                    long expirationTime = System.currentTimeMillis() + (60 * 1000);
                    redisCart.opsForZSet().add(CLEANUP_QUEUE, cart.getUserId(), expirationTime);
                    log.warn("RECONCILE cart:cleanup:queue — added missing entry for user {}",
                        cart.getUserId());
                }
            }
        }

        return fixes;
    }
}
