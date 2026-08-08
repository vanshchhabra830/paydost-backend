package com.example.paydost.service;

import com.example.paydost.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based rate limiter for login attempts.
 *
 * WHY REDIS (not a DB table):
 * - Redis INCR is atomic and sub-millisecond — ideal for the hot path before auth.
 * - Built-in TTL means counters auto-expire after the window. No cleanup job needed.
 * - A DB table would require: INSERT/UPDATE per attempt + a cron to purge expired rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.login.max-attempts}")
    private int maxAttempts;

    @Value("${app.rate-limit.login.window-seconds}")
    private long windowSeconds;

    private static final String LOGIN_ATTEMPTS_PREFIX = "login_attempts:";

    /**
     * Check if the email has exceeded the rate limit.
     * Throws TooManyRequestsException if the limit is breached.
     */
    public void checkRateLimit(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        Object value = redisTemplate.opsForValue().get(key);
        int attempts = value != null ? Integer.parseInt(value.toString()) : 0;

        if (attempts >= maxAttempts) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            throw new TooManyRequestsException(
                    "Too many login attempts. Please try again in " + (ttl != null ? ttl : windowSeconds) + " seconds.");
        }
    }

    /**
     * Record a failed login attempt. Sets TTL on the first attempt in the window.
     */
    public void recordFailedAttempt(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);

        // Set TTL only on the first attempt (count == 1) to start the window
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        log.warn("Failed login attempt {} of {} for email: {}", count, maxAttempts, email);
    }

    /**
     * Reset the counter on successful login.
     */
    public void resetAttempts(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        redisTemplate.delete(key);
    }
}
