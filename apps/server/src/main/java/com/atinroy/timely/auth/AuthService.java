package com.atinroy.timely.auth;

import com.atinroy.timely.common.UnauthorizedException;
import com.atinroy.timely.user.User;
import com.atinroy.timely.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthService {
    public record Login(String token, Instant expiresAt, User user) {
    }

    private static final String PASSWORD = "password";
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(30);

    private final UserService users;
    private final AuthIdentityRepository identities;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    // Checked when the email is unknown, so the response takes as long as a real login.
    private final String dummyHash;

    public AuthService(UserService users, AuthIdentityRepository identities, AuthTokenRepository tokens, PasswordEncoder encoder) {
        this.users = users;
        this.identities = identities;
        this.tokens = tokens;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("dummy");
    }

    @Transactional
    public Login signup(String username, String email, String password, String timezone) {
        User user = users.create(username, email, timezone != null ? timezone : "UTC");
        AuthIdentity identity = new AuthIdentity();
        identity.setId(new AuthIdentity.Key(PASSWORD, user.getId().toString()));
        identity.setUser(user);
        identity.setPasswordHash(encoder.encode(password));
        identities.save(identity);
        return issueToken(user, null);
    }

    // An unknown email and a wrong password fail the same way.
    @Transactional
    public Login login(String email, String password, String deviceName) {
        Optional<User> user = users.findByEmail(email);
        String hash = user
                .flatMap(u -> identities.findById(new AuthIdentity.Key(PASSWORD, u.getId().toString())))
                .map(AuthIdentity::getPasswordHash)
                .orElse(dummyHash);
        if (!encoder.matches(password, hash) || user.isEmpty()) {
            throw new UnauthorizedException("invalid-credentials");
        }
        return issueToken(user.get(), deviceName);
    }

    @Transactional(readOnly = true)
    public Optional<AuthPrincipal> authenticate(String token) {
        byte[] hash = hash(token);
        return tokens.findByTokenHashAndExpiresAtAfter(hash, Instant.now())
                .map(t -> new AuthPrincipal(t.getUser().getId(), hash));
    }

    @Transactional
    public void logout(byte[] tokenHash) {
        tokens.deleteByTokenHash(tokenHash);
    }

    @Transactional
    public void logoutAll(Integer userId) {
        tokens.deleteAllByUserId(userId);
    }

    private Login issueToken(User user, String deviceName) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthToken row = new AuthToken();
        row.setUser(user);
        row.setTokenHash(hash(token));
        row.setDeviceName(deviceName);
        row.setExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        tokens.save(row);
        return new Login(token, row.getExpiresAt(), user);
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
