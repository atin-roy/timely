package com.atinroy.timely.auth;

import com.atinroy.timely.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // SHA-256 of the token; the token itself is never stored.
    @Column(nullable = false, unique = true)
    private byte[] tokenHash;

    private String deviceName;

    @Generated
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
