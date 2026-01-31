package dev.atinroy.backend.controller;

import dev.atinroy.backend.dto.settings.UserSettingsRequest;
import dev.atinroy.backend.dto.settings.UserSettingsResponse;
import dev.atinroy.backend.security.UserDetailsImpl;
import dev.atinroy.backend.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public ResponseEntity<UserSettingsResponse> getUserSettings(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UserSettingsResponse settings = userSettingsService.getSettingsByUser(userDetails.getId());
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<UserSettingsResponse> updateUserSettings(
            @Valid @RequestBody UserSettingsRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        UserSettingsResponse settings = userSettingsService.updateSettings(userDetails.getId(), request);
        return ResponseEntity.ok(settings);
    }
}
