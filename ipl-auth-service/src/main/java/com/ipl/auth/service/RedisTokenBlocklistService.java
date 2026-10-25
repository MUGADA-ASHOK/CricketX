package com.ipl.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed access-token blocklist.
 *
 * Key shape: blacklist:access:{jti}
 * TTL = remaining lifetime of the access token at the moment of logout, so
 * the entry disappears from Redis on its own once the token would have
 * expired anyway - no blocklisted token is ever stored forever, and no
 * separate cleanup job is needed.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RedisTokenBlocklistService implements TokenBlocklistService {

    private static final String KEY_PREFIX = "blacklist:access:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blocklist(String jti, long ttlMillis) {
        if (ttlMillis <= 0) {
            // Token is already expired/about to expire - nothing to blocklist.
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "true", Duration.ofMillis(ttlMillis));
        } catch (Exception ex) {
            log.warn("Redis unavailable for blocklist storage (jti={}): {}", jti, ex.getMessage());
        }
    }

    @Override
    public boolean isBlocklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception ex) {
            log.warn("Redis unavailable for blocklist check (jti={}): {}", jti, ex.getMessage());
            return false;
        }
    }
}
