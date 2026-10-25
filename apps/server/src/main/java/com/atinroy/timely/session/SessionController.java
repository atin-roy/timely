package com.atinroy.timely.session;

import com.atinroy.timely.api.SessionsApi;
import com.atinroy.timely.api.model.LogSessionRequestDto;
import com.atinroy.timely.api.model.SessionDto;
import com.atinroy.timely.api.model.SessionKindDto;
import com.atinroy.timely.api.model.SessionPageDto;
import com.atinroy.timely.api.model.StartBreakRequestDto;
import com.atinroy.timely.api.model.StartCountdownRequestDto;
import com.atinroy.timely.api.model.StartStopwatchRequestDto;
import com.atinroy.timely.api.model.UpdateSessionRequestDto;
import com.atinroy.timely.auth.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;

@RestController
@RequiredArgsConstructor
public class SessionController implements SessionsApi {
    private final SessionService sessions;

    @Override
    public ResponseEntity<SessionPageDto> listSessions(OffsetDateTime from, OffsetDateTime to, SessionKindDto kind,
                                                       Integer activityId, Long pomodoroCycleId, Integer limit,
                                                       String cursor) {
        var page = sessions.list(AuthPrincipal.currentUserId(), instant(from), instant(to),
                kind == null ? null : SessionKind.valueOf(kind.name()), activityId, pomodoroCycleId, limit, cursor);
        return ResponseEntity.ok(new SessionPageDto()
                .items(page.items().stream().map(SessionMapper::toDto).toList())
                .nextCursor(page.nextCursor()));
    }

    @Override
    public ResponseEntity<SessionDto> logSession(LogSessionRequestDto request) {
        Session session = sessions.log(AuthPrincipal.currentUserId(), SessionKind.valueOf(request.getKind().name()),
                request.getActivityId(), request.getTimezone(),
                request.getStartedAt().toInstant(), request.getEndedAt().toInstant());
        return created(session);
    }

    @Override
    public ResponseEntity<SessionDto> getCurrentSession() {
        return sessions.current(AuthPrincipal.currentUserId())
                .map(session -> ResponseEntity.ok(SessionMapper.toDto(session)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    public ResponseEntity<SessionDto> stopCurrentSession() {
        return ResponseEntity.ok(SessionMapper.toDto(sessions.stop(AuthPrincipal.currentUserId())));
    }

    @Override
    public ResponseEntity<SessionDto> startStopwatch(StartStopwatchRequestDto request) {
        return created(sessions.startStopwatch(AuthPrincipal.currentUserId(), request.getActivityId(), request.getTimezone()));
    }

    @Override
    public ResponseEntity<SessionDto> startCountdown(StartCountdownRequestDto request) {
        return created(sessions.startCountdown(AuthPrincipal.currentUserId(), request.getActivityId(),
                request.getTimezone(), request.getPlannedSeconds()));
    }

    @Override
    public ResponseEntity<SessionDto> startBreak(StartBreakRequestDto request) {
        return created(sessions.startBreak(AuthPrincipal.currentUserId(), request.getTimezone(), request.getPlannedSeconds()));
    }

    @Override
    public ResponseEntity<SessionDto> getSession(Long sessionId) {
        return ResponseEntity.ok(SessionMapper.toDto(sessions.get(AuthPrincipal.currentUserId(), sessionId)));
    }

    @Override
    public ResponseEntity<SessionDto> updateSession(Long sessionId, UpdateSessionRequestDto request) {
        Session session = sessions.update(AuthPrincipal.currentUserId(), sessionId,
                instant(request.getStartedAt()), instant(request.getEndedAt()), request.getActivityId());
        return ResponseEntity.ok(SessionMapper.toDto(session));
    }

    @Override
    public ResponseEntity<Void> deleteSession(Long sessionId) {
        sessions.delete(AuthPrincipal.currentUserId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<SessionDto> created(Session session) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionMapper.toDto(session));
    }

    private static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
