package com.seregamazur.pulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    private final ObjectMapper mapper = new ObjectMapper();
    @Value("classpath:redis-reserve-stocks-script.lua")
    private Resource redisScript;

    //To work with cart and IDs
    @Bean
    public RedisTemplate<String, Object> redisCart(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        template.setHashValueSerializer(new GenericToStringSerializer<>(Object.class));
        template.setValueSerializer(new GenericToStringSerializer<>(Object.class));

        return template;
    }

    //To work with stocks and reservations
    @Bean
    public RedisTemplate<String, Long> redisStock(RedisConnectionFactory factory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        template.setKeySerializer(new StringRedisSerializer());

        template.setValueSerializer(new GenericToStringSerializer<>(Long.class));

        return template;
    }

    @Bean
    public RedisScript<Long> reserveStocksInRedisScript() {
        return RedisScript.of(redisScript, Long.class);
    }


}