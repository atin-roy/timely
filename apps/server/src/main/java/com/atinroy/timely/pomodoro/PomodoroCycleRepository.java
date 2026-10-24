package com.atinroy.timely.pomodoro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PomodoroCycleRepository extends JpaRepository<PomodoroCycle, Long> {
    Optional<PomodoroCycle> findByIdAndUserId(Long id, Integer userId);

    Optional<PomodoroCycle> findFirstByUserIdAndEndedAtIsNullOrderByIdDesc(Integer userId);
}
