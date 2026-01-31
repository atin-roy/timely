package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.stats.StreakResponse;
import dev.atinroy.backend.entity.User;
import dev.atinroy.backend.entity.UserStreak;
import dev.atinroy.backend.exception.ResourceNotFoundException;
import dev.atinroy.backend.mapper.UserStreakMapper;
import dev.atinroy.backend.repository.UserStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserStreakService {

    private final UserStreakRepository userStreakRepository;
    private final UserStreakMapper userStreakMapper;

    public StreakResponse getStreakByUser(Long userId) {
        UserStreak streak = getUserStreak(userId);
        return userStreakMapper.toResponse(streak);
    }

    @Transactional
    public StreakResponse updateStreakOnActivity(Long userId, LocalDate activityDate) {
        UserStreak streak = getUserStreak(userId);
        streak.updateStreak(activityDate);
        UserStreak updatedStreak = userStreakRepository.save(streak);
        return userStreakMapper.toResponse(updatedStreak);
    }

    @Transactional
    public StreakResponse resetStreak(Long userId) {
        UserStreak streak = getUserStreak(userId);
        streak.resetStreak();
        UserStreak updatedStreak = userStreakRepository.save(streak);
        return userStreakMapper.toResponse(updatedStreak);
    }

    public boolean isStreakActive(Long userId) {
        UserStreak streak = getUserStreak(userId);
        return streak.isStreakActive();
    }

    @Transactional
    public UserStreak createDefaultStreak(User user) {
        UserStreak streak = new UserStreak();
        streak.setUser(user);
        return userStreakRepository.save(streak);
    }

    // Helper method

    private UserStreak getUserStreak(Long userId) {
        return userStreakRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserStreak", "userId", userId));
    }
}
