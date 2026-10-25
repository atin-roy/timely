package com.atinroy.timely.pomodoro;

import com.atinroy.timely.activity.ActivityService;
import com.atinroy.timely.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against the compose Postgres. Cycles are moved into the past by shifting their running session's start.
// Every user it creates has a @pomodoro.test email and is deleted afterwards.
@SpringBootTest
class PomodoroServiceTest {
    @Autowired
    PomodoroService pomodoro;

    @Autowired
    UserService users;

    @Autowired
    ActivityService activities;

    @Autowired
    JdbcTemplate jdbc;

    private Integer userId;
    private Integer activityId;

    @BeforeEach
    void createUserAndActivity() {
        userId = users.create("atin", UUID.randomUUID() + "@pomodoro.test", "UTC").getId();
        activityId = activities.create(userId, "Coding", "#4f46e5").activity().getId();
    }

    @AfterEach
    void deleteTestUsers() {
        jdbc.update("delete from users where email like '%@pomodoro.test'");
    }

    // Default settings: 25 minute focus, 5 minute short break, 15 minute long break.
    @Test
    void waitsForTheNextFocusUntilOneLongBreakAfterTheBreakEnds() {
        Long cycle = pomodoro.start(userId, activityId, "UTC").cycle().getId();
        startedAgo(Duration.ofMinutes(25 + 5 + 10));

        assertThat(pomodoro.get(userId, cycle).status()).isEqualTo(PomodoroService.Status.WAITING);
        assertThat(endedAt(cycle)).isNull();
    }

    @Test
    void aTimedOutCycleEndsAtItsDeadlineNotWhenItIsNoticed() {
        Long cycle = pomodoro.start(userId, activityId, "UTC").cycle().getId();
        Instant start = startedAgo(Duration.ofHours(3));

        assertThat(pomodoro.get(userId, cycle).status()).isEqualTo(PomodoroService.Status.ENDED);
        assertThat(endedAt(cycle)).isEqualTo(start.plus(Duration.ofMinutes(25 + 5 + 15)));
    }

    @Test
    void aCycleWhoseBreakWasDeletedNeverEndsInTheFuture() {
        Long cycle = pomodoro.start(userId, activityId, "UTC").cycle().getId();
        startedAgo(Duration.ofMinutes(26));
        pomodoro.get(userId, cycle);
        jdbc.update("delete from sessions where pomodoro_cycle_id = ? and kind = 'break'", cycle);

        Instant before = Instant.now();
        assertThat(pomodoro.get(userId, cycle).status()).isEqualTo(PomodoroService.Status.ENDED);

        assertThat(endedAt(cycle)).isBetween(before.minusMillis(1), Instant.now());
    }

    private Instant startedAgo(Duration ago) {
        return jdbc.queryForObject(
                "update sessions set started_at = now() - ?::interval where user_id = ? and ended_at is null returning started_at",
                OffsetDateTime.class, ago.toSeconds() + " seconds", userId).toInstant();
    }

    private Instant endedAt(Long cycle) {
        OffsetDateTime endedAt = jdbc.queryForObject(
                "select ended_at from pomodoro_cycles where id = ?", OffsetDateTime.class, cycle);
        return endedAt == null ? null : endedAt.toInstant();
    }
}
