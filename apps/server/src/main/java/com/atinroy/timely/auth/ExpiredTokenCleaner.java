package com.atinroy.timely.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiredTokenCleaner {
    private final AuthTokenRepository tokens;

    @Scheduled(fixedDelay = 7, timeUnit = TimeUnit.DAYS)
    @Transactional
    public void cleanExpiredTokens() {
        int deleted = tokens.deleteExpired(Instant.now());
        log.info("Expired token cleanup completed. Deleted {} tokens", deleted);
    }
}
