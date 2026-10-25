package com.atinroy.timely.user;

public record PomodoroSettings(int focusMinutes, int shortBreakMinutes, int longBreakMinutes,
                               int pomodorosPerLongBreak) {
}
