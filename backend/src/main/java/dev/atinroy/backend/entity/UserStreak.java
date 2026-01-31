package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "user_streaks", indexes = {
        @Index(name = "idx_user_streak_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserStreak extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Integer currentStreak = 0;

    @Column(nullable = false)
    private Integer bestStreak = 0;

    @Column
    private LocalDate lastActivityDate;

    @Column
    private LocalDate streakStartDate;

    public void updateStreak(LocalDate activityDate) {
        if (lastActivityDate == null) {
            // First activity ever
            currentStreak = 1;
            bestStreak = 1;
            streakStartDate = activityDate;
            lastActivityDate = activityDate;
            return;
        }

        if (activityDate.isEqual(lastActivityDate)) {
            return;
        }

        if (activityDate.isEqual(lastActivityDate.plusDays(1))) {
            currentStreak++;
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak;
            }
            lastActivityDate = activityDate;
            return;
        }

        if (activityDate.isAfter(lastActivityDate.plusDays(1))) {
            currentStreak = 1;
            streakStartDate = activityDate;
            lastActivityDate = activityDate;
        }
    }

    public boolean isStreakActive() {
        if (lastActivityDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return lastActivityDate.isEqual(today) || lastActivityDate.isEqual(today.minusDays(1));
    }

    public void resetStreak() {
        currentStreak = 0;
        streakStartDate = null;
    }
}
