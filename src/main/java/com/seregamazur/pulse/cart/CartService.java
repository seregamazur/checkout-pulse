package com.seregamazur.pulse.cart;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.seregamazur.pulse.infra.RedisStockProvider;

import jakarta.transaction.Transactional;

@Service
public class CartService {

    private final RedisTemplate<String, Object> redisCart;
    private final RedisTemplate<String, Long> redisStock;
    private final RedisScript<Long> reserveScript;
    private final RedisStockProvider stockProvider;
    private final CartRepository cartRepository;

    public CartService(
        @Qualifier("redisCart") RedisTemplate<String, Object> redisCart,
        @Qualifier("redisStock") RedisTemplate<String, Long> redisStock,
        RedisScript<Long> reserveScript,
        RedisStockProvider stockProvider,
        CartRepository cartRepository) {
        this.redisCart = redisCart;
        this.redisStock = redisStock;
        this.reserveScript = reserveScript;
        this.stockProvider = stockProvider;
        this.cartRepository = cartRepository;
    }

    private static final String CART_PREFIX = "cart:";
    private static final String STOCK_PREFIX = "stock:";
    private static final String RESERVED_STOCK_PREFIX = "reserved:";

    //TODO
    //1. Transactional not applicable for Redis. If DB failed and we do retry -> redis state updated twice
    //2. Race condition, when 2 threads in the same time gets into method, stock=2, reserved becomes 4. that's impossible.
    @Transactional
    public void updateItem(UUID userId, UUID productId, long quantity) {
        String stockName = STOCK_PREFIX + productId;
        String reservedStockName = RESERVED_STOCK_PREFIX + productId;
        String cartKey = CART_PREFIX + userId;

        // 1. Execute atomically Lua-script to prevent race condition
        Long result = redisStock.execute(reserveScript,
            List.of(stockName, reservedStockName), // KEYS
            quantity              // ARGV
        );

        // 2. If -1 from Redis -> populate cache + retry
        if (result == -1) {
            long availableStock = stockProvider.getAvailableStock(productId);
            // setIfAbsent to prevent 2 threads setting value
            redisStock.opsForValue().setIfAbsent(stockName, availableStock);

            // Retry reservation
            result = redisStock.execute(reserveScript,
                List.of(stockName, reservedStockName),
                quantity
            );
        }

        if (result == 0) {
            throw new IllegalArgumentException("Not enough stocks!");
        }

        // 3. Update Redis + DB
        redisCart.opsForHash().put(cartKey, productId.toString(), quantity);
        redisCart.expire(cartKey, Duration.ofMinutes(5));

        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> new Cart(userId));
        cart.updateOrAddItem(productId, quantity);

        // 4. If DB Fail -> Manual Redis Rollback
        try {
            cartRepository.save(cart);
        } catch (Exception e) {
            redisStock.opsForValue().increment(reservedStockName, -quantity);

            redisCart.opsForHash().delete(cartKey, productId.toString());

            throw new RuntimeException("Failed to save cart to DB, reservation rolled back", e);
        }
    }

    public Map<UUID, Long> getCartItems(UUID userId) {
        String redisKey = CART_PREFIX + userId;

        Map<Object, Object> entries = redisCart.opsForHash().entries(redisKey);

        if (!entries.isEmpty()) {
            return entries.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> UUID.fromString(e.getKey().toString()),
                    e -> Long.valueOf(e.getValue().toString())
                ));
        }

        //if cart is only in DB -> put it into a cache too!
        return cartRepository.findByUserId(userId)
            .map(cart -> {
                Map<UUID, Long> dbItems = cart.getItems().stream()
                    .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));

                dbItems.forEach((pid, qty) ->
                    redisCart.opsForHash().put(redisKey, pid.toString(), qty));
                redisCart.expire(redisKey, Duration.ofHours(1));

                return dbItems;
            })
            .orElse(Collections.emptyMap());
    }

}
