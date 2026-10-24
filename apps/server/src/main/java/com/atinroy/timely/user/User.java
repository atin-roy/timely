package com.atinroy.timely.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private Integer focusMinutes = 25;

    @Column(nullable = false)
    private Integer shortBreakMinutes = 5;

    @Column(nullable = false)
    private Integer longBreakMinutes = 15;

    @Column(nullable = false)
    private Integer pomodorosPerLongBreak = 4;

    @Generated
    private Instant createdAt;

    // Kept current by the set_updated_at trigger.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;
}
