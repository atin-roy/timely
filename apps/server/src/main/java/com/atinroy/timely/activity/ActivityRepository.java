package com.atinroy.timely.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Integer> {
    List<Activity> findByUserIdOrderByName(Integer userId);

    List<Activity> findByUserIdAndArchivedAtIsNullOrderByName(Integer userId);

    Optional<Activity> findByIdAndUserId(Integer id, Integer userId);

    Optional<Activity> findByUserIdAndNameIgnoreCase(Integer userId, String name);
}
