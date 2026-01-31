package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.settings.UserSettingsRequest;
import dev.atinroy.backend.dto.settings.UserSettingsResponse;
import dev.atinroy.backend.entity.User;
import dev.atinroy.backend.entity.UserSettings;
import dev.atinroy.backend.exception.ResourceNotFoundException;
import dev.atinroy.backend.mapper.UserSettingsMapper;
import dev.atinroy.backend.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserSettingsMapper userSettingsMapper;

    public UserSettingsResponse getSettingsByUser(Long userId) {
        UserSettings settings = getUserSettings(userId);
        return userSettingsMapper.toResponse(settings);
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UserSettingsRequest request) {
        UserSettings settings = getUserSettings(userId);
        userSettingsMapper.updateEntity(request, settings);
        UserSettings updatedSettings = userSettingsRepository.save(settings);
        return userSettingsMapper.toResponse(updatedSettings);
    }

    @Transactional
    public UserSettings createDefaultSettings(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        // Default values are set in the entity
        return userSettingsRepository.save(settings);
    }

    // Helper method

    private UserSettings getUserSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserSettings", "userId", userId));
    }
}
