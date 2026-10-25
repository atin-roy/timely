package com.atinroy.timely.pomodoro;

import com.atinroy.timely.common.ConflictException;
import com.atinroy.timely.common.NotFoundException;
import com.atinroy.timely.session.Session;
import com.atinroy.timely.session.SessionKind;
import com.atinroy.timely.session.SessionRepository;
import com.atinroy.timely.session.SessionService;
import com.atinroy.timely.user.User;
import com.atinroy.timely.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PomodoroService {
    // RUNNING: a focus or break session is running. WAITING: a break ran its full length and the next focus
    // session waits for the user. ENDED: stopped, or no focus session started within one long break of the last break ending.
    public enum Status {RUNNING, WAITING, ENDED}

    public record CycleState(PomodoroCycle cycle, Status status, long completedPomodoros, Session currentSession) {
    }

    private final PomodoroCycleRepository cycles;
    private final SessionRepository sessions;
    private final SessionService sessionService;
    private final UserService userService;

    // Copies the user's settings into the cycle and starts its first focus session.
    @Transactional
    public CycleState start(Integer userId, Integer activityId, String timezone) {
        current(userId).ifPresent(state -> {
            if (state.status() == Status.WAITING) {
                state.cycle().setEndedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            }
        });
        User user = userService.get(userId);
        PomodoroCycle cycle = new PomodoroCycle();
        cycle.setUser(user);
        cycle.setFocusMinutes(user.getFocusMinutes());
        cycle.setShortBreakMinutes(user.getShortBreakMinutes());
        cycle.setLongBreakMinutes(user.getLongBreakMinutes());
        cycle.setPomodorosPerLongBreak(user.getPomodorosPerLongBreak());
        cycles.saveAndFlush(cycle);
        sessionService.startCycleFocus(userId, cycle, activityId, timezone);
        return state(cycle);
    }

    @Transactional
    public Optional<CycleState> current(Integer userId) {
        sessionService.current(userId);
        return cycles.findFirstByUserIdAndEndedAtIsNullOrderByIdDesc(userId)
                .map(this::state)
                .filter(state -> state.status() != Status.ENDED);
    }

    @Transactional
    public CycleState get(Integer userId, Long id) {
        sessionService.current(userId);
        return state(find(userId, id));
    }

    // The activity defaults to the previous focus session's.
    @Transactional
    public Session startNextFocus(Integer userId, Long id, Integer activityId, String timezone) {
        sessionService.current(userId);
        PomodoroCycle cycle = find(userId, id);
        switch (state(cycle).status()) {
            case ENDED -> throw new ConflictException("cycle-ended");
            case RUNNING -> throw new ConflictException("cycle-not-waiting");
            case WAITING -> {
            }
        }
        Integer activity = activityId != null ? activityId
                : sessions.findFirstByPomodoroCycleIdAndKindOrderByStartedAtDesc(id, SessionKind.FOCUS)
                .map(s -> s.getActivity().getId())
                .orElseThrow();
        return sessionService.startCycleFocus(userId, cycle, activity, timezone);
    }

    @Transactional
    public CycleState stop(Integer userId, Long id) {
        sessionService.current(userId);
        PomodoroCycle cycle = find(userId, id);
        switch (state(cycle).status()) {
            case ENDED -> throw new ConflictException("cycle-ended");
            case RUNNING -> sessionService.stop(userId);
            case WAITING -> cycle.setEndedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
        return state(cycle);
    }

    private PomodoroCycle find(Integer userId, Long id) {
        return cycles.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("not-found"));
    }

    // Also records the end of a cycle that timed out while waiting.
    private CycleState state(PomodoroCycle cycle) {
        long completed = sessions.countByPomodoroCycleIdAndKindAndCompletedTrue(cycle.getId(), SessionKind.FOCUS);
        if (cycle.getEndedAt() != null) {
            return new CycleState(cycle, Status.ENDED, completed, null);
        }
        Optional<Session> running = sessions.findByUserIdAndEndedAtIsNull(cycle.getUser().getId())
                .filter(s -> s.getPomodoroCycle() != null && s.getPomodoroCycle().getId().equals(cycle.getId()));
        if (running.isPresent()) {
            return new CycleState(cycle, Status.RUNNING, completed, running.get());
        }
        Session last = sessions.findFirstByPomodoroCycleIdOrderByStartedAtDesc(cycle.getId()).orElseThrow();
        Instant deadline = last.getEndedAt().plus(Duration.ofMinutes(cycle.getLongBreakMinutes()));
        boolean waiting = last.getKind() == SessionKind.BREAK
                && Boolean.TRUE.equals(last.getCompleted())
                && Instant.now().isBefore(deadline);
        if (waiting) {
            return new CycleState(cycle, Status.WAITING, completed, null);
        }
        cycle.setEndedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return new CycleState(cycle, Status.ENDED, completed, null);
    }
}
