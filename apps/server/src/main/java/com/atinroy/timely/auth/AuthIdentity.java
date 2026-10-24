package com.atinroy.timely.auth;

import com.atinroy.timely.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "auth_identities")
public class AuthIdentity {
    @Embeddable
    public record Key(String provider, String providerUserId) {
    }

    @EmbeddedId
    private Key id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // Set for the password provider only.
    private String passwordHash;

    @Generated
    private Instant createdAt;

    // Kept current by the set_updated_at trigger.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;
}
