package com.atinroy.timely.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHashAndExpiresAtAfter(byte[] tokenHash, Instant now);

    @Modifying
    @Query("delete from AuthToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId")
    int deleteAllByUserId(Integer userId);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
