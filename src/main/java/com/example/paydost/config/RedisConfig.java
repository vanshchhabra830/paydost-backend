package com.example.paydost.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * WHY REDIS FOR RATE LIMITING & IDEMPOTENCY CACHING:
 * ───────────────────────────────────────────────────
 * Redis is an in-memory data store — reads/writes are sub-millisecond, compared to
 * ~5-10ms for a typical MySQL query. This matters for:
 *
 * 1. RATE LIMITING: On every login attempt, we increment a counter and check the limit.
 *    This happens on the hot path BEFORE authentication. Using MySQL here would add
 *    unnecessary latency to every login request. Redis's atomic INCR + built-in TTL
 *    is a perfect fit — counters auto-expire, no cleanup cron needed.
 *
 * 2. IDEMPOTENCY CACHING: Before every transfer, we check if this referenceId was
 *    already processed. Redis gives us a fast first-check (cache hit = instant return)
 *    while the DB remains the source of truth. The 24h TTL auto-evicts old entries,
 *    keeping memory usage bounded.
 *
 * A database table COULD work for both, but would be slower, require manual TTL
 * cleanup (scheduled jobs to purge expired rows), and add write load to the DB
 * on every login attempt.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
