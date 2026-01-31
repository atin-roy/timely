package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "time_blocks",
        indexes = {
                @Index(name = "idx_user_purpose", columnList = "user_id, purpose"),
                @Index(name = "idx_user_started_at", columnList = "user_id, started_at"),
                @Index(name = "idx_tag_id", columnList = "tag_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TimeBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockPurpose purpose; // FOCUS, SHORT_BREAK, LONG_BREAK

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockMode mode; // TIMER, STOPWATCH

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt; // NULL = currently running

    /**
     * For TIMER mode only - the intended duration when timer was started.
     * NULL for STOPWATCH mode.
     */
    private Long plannedDurationSeconds;

    /**
     * Actual duration calculated from startedAt and endedAt.
     * Automatically calculated in lifecycle callbacks.
     * NULL if the block is still running (endedAt is NULL).
     */
    private Long actualDurationSeconds;

    /**
     * For TIMER mode only - whether the timer reached its planned duration.
     * TRUE: User completed the full duration
     * FALSE: User stopped early
     * NULL: For STOPWATCH mode or still running
     */
    private Boolean completed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag; // NULL = "General/Uncategorized"

    @Column(length = 500)
    private String notes;

    // ============================================
    // Lifecycle Callbacks
    // ============================================

    @PrePersist
    @PreUpdate
    private void validateAndCalculate() {
        validateTemporalConstraints();
        validateModeConstraints();
        calculateDuration();
        calculateCompletion();
    }

    // ============================================
    // Validation Methods
    // ============================================

    private void validateTemporalConstraints() {
        if (startedAt == null) {
            throw new IllegalStateException("TimeBlock must have a start time");
        }

        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalStateException(
                    "TimeBlock cannot end before it starts (started: " + startedAt + ", ended: " + endedAt + ")"
            );
        }

        if (endedAt != null && endedAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalStateException("TimeBlock cannot end in the future");
        }
    }

    private void validateModeConstraints() {
        if (mode == null) {
            throw new IllegalStateException("TimeBlock must have a mode (TIMER or STOPWATCH)");
        }

        if (purpose == null) {
            throw new IllegalStateException("TimeBlock must have a purpose (FOCUS, SHORT_BREAK, LONG_BREAK)");
        }

        // Stopwatch cannot have a planned duration
        if (mode == BlockMode.STOPWATCH && plannedDurationSeconds != null) {
            throw new IllegalStateException("STOPWATCH mode cannot have a planned duration");
        }

        // Timer must have a planned duration
        if (mode == BlockMode.TIMER && plannedDurationSeconds == null) {
            throw new IllegalStateException("TIMER mode requires a planned duration");
        }

        // Planned duration must be positive
        if (plannedDurationSeconds != null && plannedDurationSeconds <= 0) {
            throw new IllegalStateException("Planned duration must be positive");
        }
    }

    // ============================================
    // Auto-calculation Methods
    // ============================================

    private void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            this.actualDurationSeconds = ChronoUnit.SECONDS.between(startedAt, endedAt);

            // Sanity check
            if (this.actualDurationSeconds < 0) {
                throw new IllegalStateException("Calculated duration cannot be negative");
            }
        } else {
            // Block is still running
            this.actualDurationSeconds = null;
        }
    }

    private void calculateCompletion() {
        // Only applicable to TIMER mode with a finished block
        if (mode == BlockMode.TIMER && endedAt != null && plannedDurationSeconds != null) {
            // Allow a 5-second grace period (user might stop the timer 1-2 seconds early)
            long gracePeriodSeconds = 5;
            this.completed = actualDurationSeconds != null
                    && actualDurationSeconds >= (plannedDurationSeconds - gracePeriodSeconds);
        } else {
            // STOPWATCH mode or still running - completion doesn't apply
            this.completed = null;
        }
    }

    // ============================================
    // Business Logic Helpers
    // ============================================

    /**
     * Check if this time block is currently running
     */
    public boolean isActive() {
        return endedAt == null;
    }

    /**
     * Get the current duration (for active blocks) or final duration (for ended blocks)
     */
    public Long getCurrentDurationSeconds() {
        if (endedAt != null) {
            return actualDurationSeconds;
        }

        if (startedAt != null) {
            return ChronoUnit.SECONDS.between(startedAt, LocalDateTime.now());
        }

        return 0L;
    }

    /**
     * For TIMER mode: get remaining time in seconds
     * Returns NULL for STOPWATCH mode or ended blocks
     */
    public Long getRemainingSeconds() {
        if (mode != BlockMode.TIMER || endedAt != null || plannedDurationSeconds == null) {
            return null;
        }

        long elapsed = getCurrentDurationSeconds();
        long remaining = plannedDurationSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Check if a timer has exceeded its planned duration (for overrun detection)
     */
    public boolean hasOverrun() {
        if (mode != BlockMode.TIMER || plannedDurationSeconds == null) {
            return false;
        }

        Long current = getCurrentDurationSeconds();
        return current != null && current > plannedDurationSeconds;
    }
}