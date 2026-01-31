package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "time_blocks", indexes = {
        @Index(name = "idx_user_purpose", columnList = "user_id, purpose"),
        @Index(name = "idx_user_started_at", columnList = "user_id, started_at"),
        @Index(name = "idx_todo_id", columnList = "todo_id"),
        @Index(name = "idx_tag_id", columnList = "tag_id")
})
@Getter
@Setter
@NoArgsConstructor
public class TimeBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Optional: The specific Todo being worked on.
     * NULL for: breaks, general focus time, or when only tag is specified.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id")
    private Todo todo;

    /**
     * Optional: Direct tag assignment (independent of todo).
     * Used when: working on general tasks within a category but no specific todo.
     * NULL for: breaks or uncategorized time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockPurpose purpose; // FOCUS, SHORT_BREAK, LONG_BREAK

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockMode mode; // TIMER, STOPWATCH

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Long plannedDurationSeconds;
    private Long actualDurationSeconds;
    private Boolean completed;

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
        validateTagTodoRelationship();
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
            throw new IllegalStateException("TimeBlock cannot end before it starts");
        }
    }

    private void validateModeConstraints() {
        if (mode == BlockMode.STOPWATCH && plannedDurationSeconds != null) {
            throw new IllegalStateException("STOPWATCH mode cannot have planned duration");
        }

        if (mode == BlockMode.TIMER && plannedDurationSeconds == null) {
            throw new IllegalStateException("TIMER mode requires planned duration");
        }

        if (plannedDurationSeconds != null && plannedDurationSeconds <= 0) {
            throw new IllegalStateException("Planned duration must be positive");
        }
    }

    private void validateTagTodoRelationship() {
        // Break blocks should have neither todo nor tag
        if (purpose == BlockPurpose.SHORT_BREAK || purpose == BlockPurpose.LONG_BREAK) {
            if (todo != null) {
                throw new IllegalStateException("Break blocks cannot be associated with a todo");
            }
            if (tag != null) {
                throw new IllegalStateException("Break blocks cannot have a tag");
            }
        }

        // If todo is set and has a tag, timeblock MUST have the same tag
        if (todo != null && todo.getTag() != null) {
            if (tag == null) {
                throw new IllegalStateException(
                        "TimeBlock must have a tag when Todo has a tag (todo tag: " +
                                todo.getTag().getId() + ")");
            }
            if (!todo.getTag().getId().equals(tag.getId())) {
                throw new IllegalStateException(
                        "TimeBlock tag must match Todo tag (todo has tag: " +
                                todo.getTag().getId() + ", block has tag: " + tag.getId() + ")");
            }
        }

        // If todo is set but has no tag, timeblock may have any tag or no tag (no
        // validation needed)
        // If todo is not set, timeblock may have any tag or no tag (no validation
        // needed)
    }

    private void calculateDuration() {
        if (startedAt != null && endedAt != null) {
            this.actualDurationSeconds = ChronoUnit.SECONDS.between(startedAt, endedAt);
        } else {
            this.actualDurationSeconds = null;
        }
    }

    private void calculateCompletion() {
        if (mode == BlockMode.TIMER && endedAt != null && plannedDurationSeconds != null) {
            long gracePeriodSeconds = 5;
            this.completed = actualDurationSeconds != null
                    && actualDurationSeconds >= (plannedDurationSeconds - gracePeriodSeconds);
        } else {
            this.completed = null;
        }
    }

    // ============================================
    // Business Logic Helpers
    // ============================================

    /**
     * Get the effective tag for this time block.
     * Priority:
     * 1. Todo's tag (if todo exists and has tag)
     * 2. Direct tag assignment
     * 3. NULL (uncategorized)
     */
    public Tag getEffectiveTag() {
        if (todo != null && todo.getTag() != null) {
            return todo.getTag();
        }
        return tag;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public Long getCurrentDurationSeconds() {
        if (endedAt != null) {
            return actualDurationSeconds;
        }

        if (startedAt != null) {
            return ChronoUnit.SECONDS.between(startedAt, LocalDateTime.now());
        }

        return 0L;
    }

    public Long getRemainingSeconds() {
        if (mode != BlockMode.TIMER || endedAt != null || plannedDurationSeconds == null) {
            return null;
        }

        long elapsed = getCurrentDurationSeconds();
        long remaining = plannedDurationSeconds - elapsed;
        return Math.max(0, remaining);
    }

    public boolean hasOverrun() {
        if (mode != BlockMode.TIMER || plannedDurationSeconds == null) {
            return false;
        }

        Long current = getCurrentDurationSeconds();
        return current != null && current > plannedDurationSeconds;
    }
}