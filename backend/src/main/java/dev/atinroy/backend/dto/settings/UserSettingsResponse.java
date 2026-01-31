package dev.atinroy.backend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsResponse {

    private Long id;
    private Integer focusDurationMinutes;
    private Integer shortBreakMinutes;
    private Integer longBreakMinutes;
    private Integer sessionsBeforeLongBreak;
    private Boolean soundEnabled;
    private Boolean notificationsEnabled;
    private Integer soundVolume;
    private String theme;
    private Boolean showSeconds;
    private Boolean autoStartBreaks;
    private Boolean autoStartFocus;
    private Integer dailyGoalMinutes;
    private Integer dailySessionGoal;
}
