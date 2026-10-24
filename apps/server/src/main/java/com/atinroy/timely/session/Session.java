package com.atinroy.timely.session;

import com.atinroy.timely.activity.Activity;
import com.atinroy.timely.pomodoro.PomodoroCycle;
import com.atinroy.timely.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "sessions")
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private SessionKind kind;

    // Set for focus sessions only.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private SessionSource source;

    // Null for logged sessions.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private SessionMode mode;

    // Set for countdowns only.
    private Integer plannedSeconds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pomodoro_cycle_id")
    private PomodoroCycle pomodoroCycle;

    // The timezone the user was in, which fixes the session's days for good.
    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    // A generated column in the database.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Boolean completed;

    @Generated
    private Instant createdAt;

    // Kept current by the set_updated_at trigger.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;
}
