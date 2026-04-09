package com.seregamazur.pulse.infra;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RedisCartSweeper {

    private final RedisTemplate<String, Object> cartRedis;
    private final RedisScript<Long> removeReserveRedisScript;

    public RedisCartSweeper(@Qualifier("redisCart") RedisTemplate<String, Object> cartRedis,
        @Qualifier("removeReserveRedisScript") RedisScript<Long> removeReserveRedisScript) {
        this.cartRedis = cartRedis;
        this.removeReserveRedisScript = removeReserveRedisScript;
    }

    @Scheduled(fixedRate = 60000)
    public void sweepExpiredCarts() {
        String queueKey = "cart:cleanup:queue";
        String now = String.valueOf(System.currentTimeMillis());

        Long result = cartRedis.execute(removeReserveRedisScript, List.of(), now);

        log.info("Successfully cleaned {} expired carts from cache!", result);
    }

    /**
     * race condition. but why? it is scheduled, what's the issue here
     * Answer: good example is multiple instances of this app. If this method starts at the same time for 2 instances -> race condition
     * @param userId
     */
//    private void cleanupCart(UUID userId) {
//        String cartKey = "cart:" + userId;
//        String queueKey = "cart:cleanup:queue";
//
//        Map<Object, Object> entries = cartRedis.opsForHash().entries(cartKey);
//
//        // 1. decrement reserved items
//        if (!entries.isEmpty()) {
//            Map<UUID, Long> collect = entries.entrySet().stream()
//                .collect(Collectors.toMap(
//                    e -> UUID.fromString(e.getKey().toString()),
//                    e -> Long.valueOf(e.getValue().toString())
//                ));
//            for (Map.Entry<UUID, Long> entry : collect.entrySet()) {
//                cartRedis.opsForValue().decrement("reserved:" + entry.getKey(), entry.getValue());
//            }
//
//        }
//
//        // 2. delete cart
//        cartRedis.delete(cartKey);
//        cartRedis.opsForZSet().remove(queueKey, userId);
//
//        log.info("Cart for user {} has been fully cleaned up", userId);
//    }
}