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
public class RedisTokenBlocklistService implements TokenBlocklistService {

    private static final String KEY_PREFIX = "blacklist:access:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blocklist(String jti, long ttlMillis) {
        if (ttlMillis <= 0) {
            // Token is already expired/about to expire - nothing to blocklist.
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "true", Duration.ofMillis(ttlMillis));
    }

    @Override
    public boolean isBlocklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
