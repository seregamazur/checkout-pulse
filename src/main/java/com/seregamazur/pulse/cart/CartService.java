package com.seregamazur.pulse.cart;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CartRepository cartRepository;

    private static final String CART_PREFIX = "cart:";

    @Transactional
    public void updateItem(UUID userId, UUID productId, int quantity) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseGet(() -> new Cart(userId));

        cart.updateOrAddItem(productId, quantity);
        cartRepository.save(cart);

        String redisKey = CART_PREFIX + userId;
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(redisKey, productId.toString());
        } else {
            redisTemplate.opsForHash().put(redisKey, productId.toString(), quantity);
        }
        redisTemplate.expire(redisKey, Duration.ofHours(24));
    }

    public Map<UUID, Integer> getCartItems(UUID userId) {
        String redisKey = CART_PREFIX + userId;

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);

        if (!entries.isEmpty()) {
            return entries.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> UUID.fromString(e.getKey().toString()),
                    e -> (Integer) e.getValue()
                ));
        }

        return cartRepository.findByUserId(userId)
            .map(cart -> {
                Map<UUID, Integer> dbItems = cart.getItems().stream()
                    .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));

                dbItems.forEach((pid, qty) ->
                    redisTemplate.opsForHash().put(redisKey, pid.toString(), qty));
                redisTemplate.expire(redisKey, Duration.ofHours(24));

                return dbItems;
            })
            .orElse(Collections.emptyMap());
    }

    public void clearCart(String userId) {
        redisTemplate.delete(CART_PREFIX + userId);
    }
}