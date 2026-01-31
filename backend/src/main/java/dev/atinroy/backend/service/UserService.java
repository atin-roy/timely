package dev.atinroy.backend.service;

import dev.atinroy.backend.entity.User;
import dev.atinroy.backend.exception.ResourceNotFoundException;
import dev.atinroy.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TodoRepository todoRepository;
    private final TagRepository tagRepository;
    private final TimeBlockRepository timeBlockRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserStreakRepository userStreakRepository;

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional
    public User updateUser(Long userId, String email, String username) {
        User user = getUserById(userId);

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
        }

        if (username != null && !username.equals(user.getUsername())) {
            user.setUsername(username);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = getUserById(userId);

        // Delete all related data (cascade)
        todoRepository.deleteByUserId(userId);
        tagRepository.deleteByUserId(userId);
        timeBlockRepository.deleteByUserId(userId);
        userSettingsRepository.deleteByUserId(userId);
        userStreakRepository.deleteByUserId(userId);

        userRepository.delete(user);
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }
}
