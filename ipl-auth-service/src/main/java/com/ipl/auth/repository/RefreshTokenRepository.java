package com.ipl.auth.repository;

import com.ipl.auth.entity.RefreshToken;
import com.ipl.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    /**
     * All of a user's sessions that have not been revoked yet - used to
     * revoke the entire chain when refresh-token reuse is detected.
     */
    List<RefreshToken> findByUserAndRevokedAtIsNull(User user);
}
