package com.atinroy.timely.pomodoro;

import com.atinroy.timely.api.PomodoroApi;
import com.atinroy.timely.api.model.PomodoroCycleDto;
import com.atinroy.timely.api.model.SessionDto;
import com.atinroy.timely.api.model.StartNextPomodoroFocusRequestDto;
import com.atinroy.timely.api.model.StartPomodoroCycleRequestDto;
import com.atinroy.timely.auth.AuthPrincipal;
import com.atinroy.timely.session.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PomodoroController implements PomodoroApi {
    private final PomodoroService pomodoro;

    @Override
    public ResponseEntity<PomodoroCycleDto> startPomodoroCycle(StartPomodoroCycleRequestDto request) {
        var state = pomodoro.start(AuthPrincipal.currentUserId(), request.getActivityId(), request.getTimezone());
        return ResponseEntity.status(HttpStatus.CREATED).body(PomodoroMapper.toDto(state));
    }

    @Override
    public ResponseEntity<PomodoroCycleDto> getCurrentPomodoroCycle() {
        return pomodoro.current(AuthPrincipal.currentUserId())
                .map(state -> ResponseEntity.ok(PomodoroMapper.toDto(state)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Override
    public ResponseEntity<PomodoroCycleDto> getPomodoroCycle(Long cycleId) {
        return ResponseEntity.ok(PomodoroMapper.toDto(pomodoro.get(AuthPrincipal.currentUserId(), cycleId)));
    }

    @Override
    public ResponseEntity<SessionDto> startNextPomodoroFocus(Long cycleId, StartNextPomodoroFocusRequestDto request) {
        var session = pomodoro.startNextFocus(AuthPrincipal.currentUserId(), cycleId,
                request.getActivityId(), request.getTimezone());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionMapper.toDto(session));
    }

    @Override
    public ResponseEntity<PomodoroCycleDto> stopPomodoroCycle(Long cycleId) {
        return ResponseEntity.ok(PomodoroMapper.toDto(pomodoro.stop(AuthPrincipal.currentUserId(), cycleId)));
    }
}
