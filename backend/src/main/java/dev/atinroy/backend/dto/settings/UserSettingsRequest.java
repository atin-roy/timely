package dev.atinroy.backend.dto.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsRequest {

    @Min(value = 1, message = "Focus duration must be at least 1 minute")
    private Integer focusDurationMinutes;

    @Min(value = 1, message = "Short break duration must be at least 1 minute")
    private Integer shortBreakMinutes;

    @Min(value = 1, message = "Long break duration must be at least 1 minute")
    private Integer longBreakMinutes;

    @Min(value = 1, message = "Sessions before long break must be at least 1")
    private Integer sessionsBeforeLongBreak;

    private Boolean soundEnabled;

    private Boolean notificationsEnabled;

    @Min(value = 0, message = "Volume must be between 0 and 100")
    @Max(value = 100, message = "Volume must be between 0 and 100")
    private Integer soundVolume;

    @Pattern(regexp = "LIGHT|DARK|AUTO", message = "Theme must be LIGHT, DARK, or AUTO")
    private String theme;

    private Boolean showSeconds;

    private Boolean autoStartBreaks;

    private Boolean autoStartFocus;

    @Min(value = 0, message = "Daily goal cannot be negative")
    private Integer dailyGoalMinutes;

    @Min(value = 0, message = "Daily session goal cannot be negative")
    private Integer dailySessionGoal;
}
