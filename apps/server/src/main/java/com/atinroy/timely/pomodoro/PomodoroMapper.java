package com.atinroy.timely.pomodoro;

import com.atinroy.timely.api.model.PomodoroCycleDto;
import com.atinroy.timely.api.model.PomodoroSettingsDto;
import com.atinroy.timely.session.SessionMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class PomodoroMapper {
    private PomodoroMapper() {
    }

    public static PomodoroCycleDto toDto(PomodoroService.CycleState state) {
        PomodoroCycle cycle = state.cycle();
        return new PomodoroCycleDto()
                .id(cycle.getId())
                .settings(new PomodoroSettingsDto()
                        .focusMinutes(cycle.getFocusMinutes())
                        .shortBreakMinutes(cycle.getShortBreakMinutes())
                        .longBreakMinutes(cycle.getLongBreakMinutes())
                        .pomodorosPerLongBreak(cycle.getPomodorosPerLongBreak()))
                .completedPomodoros((int) state.completedPomodoros())
                .status(PomodoroCycleDto.StatusEnum.valueOf(state.status().name()))
                .currentSession(state.currentSession() == null ? null : SessionMapper.toDto(state.currentSession()))
                .createdAt(OffsetDateTime.ofInstant(cycle.getCreatedAt(), ZoneOffset.UTC));
    }
}
