package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.auth.AuthResponse;
import dev.atinroy.backend.dto.auth.LoginRequest;
import dev.atinroy.backend.dto.auth.RegisterRequest;
import dev.atinroy.backend.entity.User;
import dev.atinroy.backend.entity.UserSettings;
import dev.atinroy.backend.entity.UserStreak;
import dev.atinroy.backend.exception.DuplicateResourceException;
import dev.atinroy.backend.exception.UnauthorizedException;
import dev.atinroy.backend.repository.UserRepository;
import dev.atinroy.backend.repository.UserSettingsRepository;
import dev.atinroy.backend.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserStreakRepository userStreakRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        // Create new user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        // Create default settings for the user
        UserSettings settings = new UserSettings();
        settings.setUser(savedUser);
        userSettingsRepository.save(settings);

        // Create default streak for the user
        UserStreak streak = new UserStreak();
        streak.setUser(savedUser);
        userStreakRepository.save(streak);

        // For now, return a simple response without JWT token
        // You'll need to integrate JWT token generation here
        return new AuthResponse(
                "token-placeholder", // Replace with actual JWT token
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        // Find user by email or username
        User user = userRepository.findByEmail(request.getEmailOrUsername())
                .or(() -> userRepository.findByUsername(request.getEmailOrUsername()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // For now, return a simple response without JWT token
        // You'll need to integrate JWT token generation here
        return new AuthResponse(
                "token-placeholder", // Replace with actual JWT token
                user.getId(),
                user.getUsername(),
                user.getEmail());
    }
}
