package com.atinroy.timely.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByIdAndUserId(Long id, Integer userId);

    Optional<Session> findByUserIdAndEndedAtIsNull(Integer userId);

    Optional<Session> findFirstByPomodoroCycleIdOrderByStartedAtDesc(Long pomodoroCycleId);

    Optional<Session> findFirstByPomodoroCycleIdAndKindOrderByStartedAtDesc(Long pomodoroCycleId, SessionKind kind);

    long countByPomodoroCycleIdAndKindAndCompletedTrue(Long pomodoroCycleId, SessionKind kind);

    // A running session has no end and occupies everything from its start onward. Pass an id that doesn't exist to exclude nothing.
    @Query("""
            select count(s) > 0 from Session s
            where s.user.id = :userId and s.id <> :excludeId
              and s.startedAt < :end and (s.endedAt is null or s.endedAt > :start)
            """)
    boolean existsOverlapping(Integer userId, Instant start, Instant end, Long excludeId);
}
