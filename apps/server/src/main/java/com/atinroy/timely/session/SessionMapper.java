package com.atinroy.timely.session;

import com.atinroy.timely.api.model.SessionDto;
import com.atinroy.timely.api.model.SessionKindDto;
import com.atinroy.timely.api.model.SessionModeDto;
import com.atinroy.timely.api.model.SessionSourceDto;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class SessionMapper {
    private static final Duration TRIM_THRESHOLD = Duration.ofHours(12);

    private SessionMapper() {
    }

    public static SessionDto toDto(Session session) {
        return new SessionDto()
                .id(session.getId())
                .kind(SessionKindDto.valueOf(session.getKind().name()))
                .activityId(session.getActivity() == null ? null : session.getActivity().getId())
                .source(SessionSourceDto.valueOf(session.getSource().name()))
                .mode(session.getMode() == null ? null : SessionModeDto.valueOf(session.getMode().name()))
                .plannedSeconds(session.getPlannedSeconds())
                .pomodoroCycleId(session.getPomodoroCycle() == null ? null : session.getPomodoroCycle().getId())
                .timezone(session.getTimezone())
                .startedAt(utc(session.getStartedAt()))
                .endedAt(session.getEndedAt() == null ? null : utc(session.getEndedAt()))
                .completed(session.getCompleted())
                .needsTrim(needsTrim(session));
    }

    private static boolean needsTrim(Session session) {
        return session.getMode() == SessionMode.STOPWATCH
                && session.getEndedAt() == null
                && session.getStartedAt().plus(TRIM_THRESHOLD).isBefore(Instant.now());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
