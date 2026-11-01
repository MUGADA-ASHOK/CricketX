package com.ipl.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-Concurrency In-Memory Flash Sale Engine.
 * Replaces database row locks with atomic Redis Lua scripts and distributed seat leases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisFlashSaleService {

    private final StringRedisTemplate redisTemplate;

    private static final String STOCK_KEY_PREFIX = "stock:match:%d:cat:%d";
    private static final String SEAT_KEY_PREFIX = "seat:match:%d:seat:%d";

    private static final String LUA_DECR_IF_AVAILABLE =
            "local stock = redis.call('get', KEYS[1])\n" +
            "if not stock or tonumber(stock) < tonumber(ARGV[1]) then\n" +
            "    return -1\n" +
            "end\n" +
            "return redis.call('decrby', KEYS[1], ARGV[1])";

    /**
     * Initializes Redis in-memory category stock.
     */
    public void syncStock(Long matchId, Long categoryId, int availableQuantity) {
        String key = String.format(STOCK_KEY_PREFIX, matchId, categoryId);
        redisTemplate.opsForValue().set(key, String.valueOf(availableQuantity), Duration.ofHours(48));
        log.info("Synced stock in Redis for key {} -> {}", key, availableQuantity);
    }

    /**
     * Atomically decrements ticket quantity using Lua Script in Redis RAM (< 1ms).
     * Returns true if reservation acquired, false if sold out.
     */
    public boolean tryReserveStock(Long matchId, Long categoryId, int quantity) {
        String key = String.format(STOCK_KEY_PREFIX, matchId, categoryId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_DECR_IF_AVAILABLE, Long.class);

        try {
            Long remaining = redisTemplate.execute(script, Collections.singletonList(key), String.valueOf(quantity));
            if (remaining != null && remaining >= 0) {
                log.info("Redis atomic decrement successful for {} (remaining: {})", key, remaining);
                return true;
            }
            log.warn("Redis atomic decrement rejected: insufficient stock for {}", key);
            return false;
        } catch (Exception ex) {
            log.warn("Redis unavailable for flash sale check ({}), falling back to DB check: {}", key, ex.getMessage());
            return true; // Fall back to DB lock if Redis connectivity fails
        }
    }

    /**
     * Restores stock back to Redis upon order cancellation or timeout.
     */
    public void releaseStock(Long matchId, Long categoryId, int quantity) {
        String key = String.format(STOCK_KEY_PREFIX, matchId, categoryId);
        try {
            redisTemplate.opsForValue().increment(key, quantity);
            log.info("Restored {} tickets in Redis for key {}", quantity, key);
        } catch (Exception ex) {
            log.warn("Failed to increment Redis stock for {}: {}", key, ex.getMessage());
        }
    }

    /**
     * Atomically leases specific seats in Redis with a 10-minute hold TTL.
     * If any single seat is already leased, rolls back all previous seat leases in this request.
     */
    public boolean tryHoldSeats(Long matchId, List<Long> seatIds, Long userId, long holdMinutes) {
        List<String> acquiredKeys = new ArrayList<>();
        Duration ttl = Duration.ofMinutes(holdMinutes);

        try {
            for (Long seatId : seatIds) {
                String seatKey = String.format(SEAT_KEY_PREFIX, matchId, seatId);
                Boolean success = redisTemplate.opsForValue().setIfAbsent(seatKey, String.valueOf(userId), ttl);
                if (Boolean.TRUE.equals(success)) {
                    acquiredKeys.add(seatKey);
                } else {
                    // Conflict detected! Rollback all acquired locks in this batch
                    log.warn("Seat {} for match {} is already leased by another fan. Rolling back batch.", seatId, matchId);
                    redisTemplate.delete(acquiredKeys);
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("Redis error holding seats: {}. Falling back to DB locking.", ex.getMessage());
            return true;
        }
    }

    /**
     * Releases seat leases from Redis.
     */
    public void releaseSeats(Long matchId, List<Long> seatIds) {
        try {
            List<String> keys = seatIds.stream()
                    .map(seatId -> String.format(SEAT_KEY_PREFIX, matchId, seatId))
                    .toList();
            redisTemplate.delete(keys);
            log.info("Released seat leases in Redis for matchId={}, seats={}", matchId, seatIds);
        } catch (Exception ex) {
            log.warn("Failed to delete seat keys from Redis: {}", ex.getMessage());
        }
    }
}

