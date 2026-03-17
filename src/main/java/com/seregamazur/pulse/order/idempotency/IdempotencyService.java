package com.seregamazur.pulse.order.idempotency;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";


//    public void startIdempotency(UUID key) {
//        if (redisTemplate.opsForHash().)
//        redisTemplate.setScriptExecutor();
//    }
}
