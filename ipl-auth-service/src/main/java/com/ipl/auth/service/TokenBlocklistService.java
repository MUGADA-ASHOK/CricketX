package com.ipl.auth.service;

/**
 * Abstraction over "has this access token's JTI been blocklisted (logged
 * out)". Kept as an interface so the authentication filter and AuthService
 * never depend on Redis APIs directly - see RedisTokenBlocklistService for
 * the actual implementation.
 */
public interface TokenBlocklistService {

    /**
     * Blocklists the given JTI for ttlMillis. Implementations should make
     * the entry expire automatically after ttlMillis rather than relying on
     * a separate cleanup job.
     */
    void blocklist(String jti, long ttlMillis);

    boolean isBlocklisted(String jti);
}
