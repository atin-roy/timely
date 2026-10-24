package com.atinroy.timely.pomodoro;

import com.atinroy.timely.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.Instant;

// Settings are copied from the user when the cycle starts, so later changes only affect future cycles.
@Getter
@Setter
@Entity
@Table(name = "pomodoro_cycles")
public class PomodoroCycle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer focusMinutes;

    @Column(nullable = false)
    private Integer shortBreakMinutes;

    @Column(nullable = false)
    private Integer longBreakMinutes;

    @Column(nullable = false)
    private Integer pomodorosPerLongBreak;

    @Generated
    private Instant createdAt;

    private Instant endedAt;
}
