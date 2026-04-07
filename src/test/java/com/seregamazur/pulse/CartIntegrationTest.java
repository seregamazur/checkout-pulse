package com.seregamazur.pulse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.seregamazur.pulse.cart.Cart;
import com.seregamazur.pulse.inventory.Product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Add item to cart — reserves stock in Redis and persists to DB")
    void updateItem_addsItemToCartInRedisAndDb() {
        Product product = createProduct("iPhone 15 Pro", new BigDecimal("999.00"), 50);
        UUID productId = product.getId();

        cartService.updateItem(USER_ID, productId, 2);

        Object cartQty = redisCart.opsForHash().get("cart:" + USER_ID, productId.toString());
        assertThat(cartQty).isNotNull();
        assertThat(Long.parseLong(cartQty.toString())).isEqualTo(2L);

        Long reserved = redisStock.opsForValue().get("reserved:" + productId);
        assertThat(reserved).isEqualTo(2L);

        Long cachedStock = redisStock.opsForValue().get("stock:" + productId);
        assertThat(cachedStock).isEqualTo(50L);

        int itemCount = inTransaction(() -> {
            Cart dbCart = cartRepository.findByUserId(USER_ID).orElseThrow();
            return dbCart.getItems().size();
        });
        assertThat(itemCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Add multiple items to cart — all stored in Redis and DB")
    void updateItem_multipleItems_allStoredCorrectly() {
        Product iphone = createProduct("iPhone", new BigDecimal("999.00"), 50);
        Product airpods = createProduct("AirPods", new BigDecimal("249.00"), 120);

        cartService.updateItem(USER_ID, iphone.getId(), 1);
        cartService.updateItem(USER_ID, airpods.getId(), 3);

        Map<Object, Object> cartEntries = redisCart.opsForHash().entries("cart:" + USER_ID);
        assertThat(cartEntries).hasSize(2);
        assertThat(Long.parseLong(cartEntries.get(iphone.getId().toString()).toString())).isEqualTo(1L);
        assertThat(Long.parseLong(cartEntries.get(airpods.getId().toString()).toString())).isEqualTo(3L);

        int itemCount = inTransaction(() -> {
            Cart dbCart = cartRepository.findByUserId(USER_ID).orElseThrow();
            return dbCart.getItems().size();
        });
        assertThat(itemCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Add item with insufficient stock — throws exception, no state change")
    void updateItem_notEnoughStock_throwsException() {
        Product keyboard = createProduct("Keyboard", new BigDecimal("150.00"), 0);

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, keyboard.getId(), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not enough stocks");

        assertThat(redisCart.opsForHash().entries("cart:" + USER_ID)).isEmpty();
        assertThat(cartRepository.findByUserId(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("Add item when Redis stock cache is already warm — reserves successfully")
    void updateItem_warmRedisCache_reservesSuccessfully() {
        Product product = createProduct("MacBook", new BigDecimal("1299.00"), 15);
        UUID productId = product.getId();

        redisStock.opsForValue().set("stock:" + productId, 15L);

        cartService.updateItem(USER_ID, productId, 2);

        Long reserved = redisStock.opsForValue().get("reserved:" + productId);
        assertThat(reserved).isEqualTo(2L);

        assertThat(cartRepository.findByUserId(USER_ID)).isPresent();
    }

    @Test
    @DisplayName("Get cart items — returns from Redis when cache present")
    void getCartItems_returnsFromRedisCache() {
        Product product = createProduct("Kindle", new BigDecimal("139.00"), 200);
        UUID productId = product.getId();

        redisCart.opsForHash().put("cart:" + USER_ID, productId.toString(), 5L);
        redisCart.expire("cart:" + USER_ID, Duration.ofMinutes(5));

        Map<UUID, Long> items = cartService.getCartItems(USER_ID);

        assertThat(items).hasSize(1);
        assertThat(items.get(productId)).isEqualTo(5L);
    }

    @Test
    @DisplayName("Get cart items — falls back to DB and caches in Redis when Redis is empty")
    void getCartItems_fallsBackToDbAndCachesInRedis() {
        Product product = createProduct("Kindle", new BigDecimal("139.00"), 200);
        UUID productId = product.getId();

        createCartWithItem(USER_ID, productId, 3);
        assertThat(redisCart.opsForHash().entries("cart:" + USER_ID)).isEmpty();

        Map<UUID, Long> items = inTransaction(() -> cartService.getCartItems(USER_ID));

        assertThat(items).hasSize(1);
        assertThat(items.get(productId)).isEqualTo(3L);

        Map<Object, Object> cached = redisCart.opsForHash().entries("cart:" + USER_ID);
        assertThat(cached).isNotEmpty();
        assertThat(Long.parseLong(cached.get(productId.toString()).toString())).isEqualTo(3L);
    }

    @Test
    @DisplayName("Get cart items — returns empty map when no cart exists")
    void getCartItems_noCart_returnsEmptyMap() {
        Map<UUID, Long> items = cartService.getCartItems(UUID.randomUUID());
        assertThat(items).isEmpty();
    }
}
