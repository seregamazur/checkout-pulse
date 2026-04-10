package com.seregamazur.pulse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.seregamazur.pulse.infra.ReconciliationService;
import com.seregamazur.pulse.inventory.Product;
import com.seregamazur.pulse.shared.outbox.DebeziumOutboxPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    DebeziumOutboxPublisher debeziumOutboxPublisher;

    @Autowired
    ReconciliationService reconciliationService;

    private static final Instant EPOCH = Instant.EPOCH;
    private static final Instant FAR_FUTURE = Instant.parse("2099-01-01T00:00:00Z");

    @Nested
    @DisplayName("Stock Reconciliation")
    class StockReconciliation {

        @Test
        @DisplayName("Redis stock corrected when it drifts from DB")
        void staleRedisStock_corrected() {
            Product product = createProduct("MacBook", new BigDecimal("1299.00"), 50);
            UUID pid = product.getId();

            redisStock.opsForValue().set("stock:" + pid, 999L);

            int fixes = reconciliationService.reconcileStock(EPOCH, FAR_FUTURE);

            assertThat(fixes).isGreaterThanOrEqualTo(1);
            assertThat(redisStock.opsForValue().get("stock:" + pid)).isEqualTo(50L);
        }

        @Test
        @DisplayName("Missing Redis stock key is populated from DB")
        void missingRedisStock_populated() {
            Product product = createProduct("iPad", new BigDecimal("799.00"), 30);
            UUID pid = product.getId();

            redisStock.delete("stock:" + pid);

            int fixes = reconciliationService.reconcileStock(EPOCH, FAR_FUTURE);

            assertThat(fixes).isGreaterThanOrEqualTo(1);
            assertThat(redisStock.opsForValue().get("stock:" + pid)).isEqualTo(30L);
        }

        @Test
        @DisplayName("Correct Redis stock is not touched")
        void correctRedisStock_noFix() {
            Product product = createProduct("AirPods", new BigDecimal("249.00"), 100);
            UUID pid = product.getId();

            redisStock.opsForValue().set("stock:" + pid, 100L);

            int fixes = reconciliationService.reconcileStock(EPOCH, FAR_FUTURE);

            assertThat(fixes).isZero();
            assertThat(redisStock.opsForValue().get("stock:" + pid)).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("Reserved Reconciliation")
    class ReservedReconciliation {

        @Test
        @DisplayName("Redis reserved corrected to match sum of DB cart items")
        void staleReserved_corrected() {
            Product product = createProduct("Watch", new BigDecimal("399.00"), 50);
            UUID pid = product.getId();

            createCartWithItem(USER_ID, pid, 3);
            UUID user2 = UUID.fromString("bbbb0000-0000-0000-0000-000000000002");
            createCartWithItem(user2, pid, 2);

            redisStock.opsForValue().set("reserved:" + pid, 999L);

            int fixes = reconciliationService.reconcileReserved(EPOCH, FAR_FUTURE);

            assertThat(fixes).isGreaterThanOrEqualTo(1);
            assertThat(redisStock.opsForValue().get("reserved:" + pid)).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("Cart Reconciliation")
    class CartReconciliation {

        @Test
        @DisplayName("Corrupted Redis cart hash is overwritten from DB")
        void corruptedCart_corrected() {
            Product product = createProduct("Speaker", new BigDecimal("199.00"), 40);
            UUID pid = product.getId();
            createCartWithItem(USER_ID, pid, 2);

            String cartKey = "cart:" + USER_ID;
            redisCart.delete(cartKey);
            redisCart.opsForHash().put(cartKey, pid.toString(), "999");

            int fixes = reconciliationService.reconcileCarts(EPOCH, FAR_FUTURE);

            assertThat(fixes).isGreaterThanOrEqualTo(1);

            Map<Object, Object> entries = redisCart.opsForHash().entries(cartKey);
            assertThat(Long.valueOf(entries.get(pid.toString()).toString())).isEqualTo(2L);
        }

        @Test
        @DisplayName("Missing Redis cart is rebuilt from DB")
        void missingCart_rebuilt() {
            Product product = createProduct("Headphones", new BigDecimal("349.00"), 25);
            UUID pid = product.getId();
            createCartWithItem(USER_ID, pid, 4);

            String cartKey = "cart:" + USER_ID;
            redisCart.delete(cartKey);

            int fixes = reconciliationService.reconcileCarts(EPOCH, FAR_FUTURE);

            assertThat(fixes).isGreaterThanOrEqualTo(1);

            Map<Object, Object> entries = redisCart.opsForHash().entries(cartKey);
            assertThat(entries).hasSize(1);
            assertThat(Long.valueOf(entries.get(pid.toString()).toString())).isEqualTo(4L);
        }

        @Test
        @DisplayName("Missing cleanup queue entry is restored for non-empty cart")
        void missingCleanupEntry_restored() {
            Product product = createProduct("Mouse", new BigDecimal("79.00"), 100);
            UUID pid = product.getId();
            createCartWithItem(USER_ID, pid, 1);

            redisCart.opsForZSet().remove("cart:cleanup:queue", USER_ID);

            reconciliationService.reconcileCarts(EPOCH, FAR_FUTURE);

            Double score = redisCart.opsForZSet().score("cart:cleanup:queue", USER_ID);
            assertThat(score).isNotNull();
        }
    }

    @Nested
    @DisplayName("Cooldown Window")
    class CooldownWindow {

        @Test
        @DisplayName("Recently modified records within cooldown are NOT reconciled")
        void recentlyModified_skippedByCooldown() {
            Product product = createProduct("Tablet", new BigDecimal("599.00"), 20);
            UUID pid = product.getId();

            redisStock.opsForValue().set("stock:" + pid, 999L);

            Instant beforeCreation = Instant.now().minusSeconds(30);
            int fixes = reconciliationService.reconcileStock(beforeCreation, beforeCreation.plusSeconds(1));

            assertThat(fixes).isZero();
            assertThat(redisStock.opsForValue().get("stock:" + pid)).isEqualTo(999L);
        }
    }
}
