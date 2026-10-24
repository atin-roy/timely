package com.atinroy.timely.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByIdAndUserId(Long id, Integer userId);

    Optional<Session> findByUserIdAndEndedAtIsNull(Integer userId);
}
