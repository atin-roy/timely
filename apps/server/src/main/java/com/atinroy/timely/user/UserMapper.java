package com.atinroy.timely.user;

import com.atinroy.timely.api.model.PomodoroSettingsDto;
import com.atinroy.timely.api.model.UserDto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class UserMapper {
    private UserMapper() {
    }

    public static UserDto toDto(User user) {
        return new UserDto()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .timezone(user.getTimezone())
                .pomodoroSettings(new PomodoroSettingsDto()
                        .focusMinutes(user.getFocusMinutes())
                        .shortBreakMinutes(user.getShortBreakMinutes())
                        .longBreakMinutes(user.getLongBreakMinutes())
                        .pomodorosPerLongBreak(user.getPomodorosPerLongBreak()))
                .createdAt(OffsetDateTime.ofInstant(user.getCreatedAt(), ZoneOffset.UTC));
    }
}
