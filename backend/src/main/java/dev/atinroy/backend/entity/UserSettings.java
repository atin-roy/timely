package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_settings", indexes = {
        @Index(name = "idx_user_settings_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Integer focusDurationMinutes = 25;

    @Column(nullable = false)
    private Integer shortBreakMinutes = 5;

    @Column(nullable = false)
    private Integer longBreakMinutes = 15;

    @Column(nullable = false)
    private Integer sessionsBeforeLongBreak = 4;

    @Column(nullable = false)
    private Boolean soundEnabled = true;

    @Column(nullable = false)
    private Boolean notificationsEnabled = true;

    @Column(nullable = false)
    private Integer soundVolume = 50;

    @Column(nullable = false, length = 10)
    private String theme = "LIGHT";

    @Column(nullable = false)
    private Boolean showSeconds = true;

    @Column(nullable = false)
    private Boolean autoStartBreaks = false;

    @Column(nullable = false)
    private Boolean autoStartFocus = false;

    @Column
    private Integer dailyGoalMinutes;

    @Column
    private Integer dailySessionGoal;

    @PrePersist
    @PreUpdate
    private void validateSettings() {
        if (focusDurationMinutes != null && focusDurationMinutes < 1) {
            throw new IllegalStateException("Focus duration must be at least 1 minute");
        }
        if (shortBreakMinutes != null && shortBreakMinutes < 1) {
            throw new IllegalStateException("Short break duration must be at least 1 minute");
        }
        if (longBreakMinutes != null && longBreakMinutes < 1) {
            throw new IllegalStateException("Long break duration must be at least 1 minute");
        }
        if (sessionsBeforeLongBreak != null && sessionsBeforeLongBreak < 1) {
            throw new IllegalStateException("Sessions before long break must be at least 1");
        }
        if (soundVolume != null && (soundVolume < 0 || soundVolume > 100)) {
            throw new IllegalStateException("Sound volume must be between 0 and 100");
        }
        if (theme != null && !theme.matches("LIGHT|DARK|AUTO")) {
            throw new IllegalStateException("Theme must be LIGHT, DARK, or AUTO");
        }
        if (dailyGoalMinutes != null && dailyGoalMinutes < 0) {
            throw new IllegalStateException("Daily goal minutes cannot be negative");
        }
        if (dailySessionGoal != null && dailySessionGoal < 0) {
            throw new IllegalStateException("Daily session goal cannot be negative");
        }
    }
}
