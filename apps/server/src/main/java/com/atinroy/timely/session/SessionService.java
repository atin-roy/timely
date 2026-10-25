package com.atinroy.timely.session;

import com.atinroy.timely.activity.Activity;
import com.atinroy.timely.activity.ActivityRepository;
import com.atinroy.timely.common.ConflictException;
import com.atinroy.timely.common.NotFoundException;
import com.atinroy.timely.common.Timezones;
import com.atinroy.timely.pomodoro.PomodoroCycle;
import com.atinroy.timely.user.UserRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");
    private static final Long NO_SESSION = 0L;

    public record SessionPage(List<Session> items, String nextCursor) {
    }

    private final SessionRepository sessions;
    private final ActivityRepository activities;
    private final UserRepository users;

    // The running session. A countdown past its planned end is ended first, as are any breaks that follow it in a pomodoro cycle.
    @Transactional
    public Optional<Session> current(Integer userId) {
        return settle(userId, now());
    }

    @Transactional
    public Session get(Integer userId, Long id) {
        settle(userId, now());
        return sessions.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("not-found"));
    }

    // Newest first. A null filter matches everything; nextCursor is null on the last page.
    @Transactional
    public SessionPage list(Integer userId, Instant from, Instant to, SessionKind kind, Integer activityId,
                            Long pomodoroCycleId, int limit, String cursor) {
        settle(userId, now());
        Instant cursorStart = null;
        Long cursorId = null;
        if (cursor != null) {
            String[] parts = decodeCursor(cursor);
            cursorStart = parseCursorStart(parts[0]);
            cursorId = Long.valueOf(parts[1]);
        }
        Instant after = cursorStart;
        Long afterId = cursorId;
        Specification<Session> filters = (root, query, cb) -> {
            Path<Instant> startedAt = root.get("startedAt");
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("user").get("id"), userId));
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(startedAt, from));
            }
            if (to != null) {
                all.add(cb.lessThan(startedAt, to));
            }
            if (kind != null) {
                all.add(cb.equal(root.get("kind"), kind));
            }
            if (activityId != null) {
                all.add(cb.equal(root.get("activity").get("id"), activityId));
            }
            if (pomodoroCycleId != null) {
                all.add(cb.equal(root.get("pomodoroCycle").get("id"), pomodoroCycleId));
            }
            if (after != null) {
                all.add(cb.or(cb.lessThan(startedAt, after),
                        cb.and(cb.equal(startedAt, after), cb.lessThan(root.<Long>get("id"), afterId))));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "startedAt", "id");
        List<Session> rows = sessions.findBy(filters, q -> q.sortBy(newestFirst).limit(limit + 1).all());
        if (rows.size() <= limit) {
            return new SessionPage(rows, null);
        }
        List<Session> items = rows.subList(0, limit);
        Session last = items.getLast();
        return new SessionPage(items, encodeCursor(last));
    }

    @Transactional
    public Session startStopwatch(Integer userId, Integer activityId, String timezone) {
        return start(userId, SessionKind.FOCUS, activityFor(userId, activityId), SessionMode.STOPWATCH, null, null, timezone);
    }

    @Transactional
    public Session startCountdown(Integer userId, Integer activityId, String timezone, int plannedSeconds) {
        return start(userId, SessionKind.FOCUS, activityFor(userId, activityId), SessionMode.COUNTDOWN, plannedSeconds, null, timezone);
    }

    // A countdown when plannedSeconds is given, otherwise a stopwatch.
    @Transactional
    public Session startBreak(Integer userId, String timezone, Integer plannedSeconds) {
        SessionMode mode = plannedSeconds == null ? SessionMode.STOPWATCH : SessionMode.COUNTDOWN;
        return start(userId, SessionKind.BREAK, null, mode, plannedSeconds, null, timezone);
    }

    // The first or next focus session of a pomodoro cycle. Breaks start on their own when a focus session ends.
    @Transactional
    public Session startCycleFocus(Integer userId, PomodoroCycle cycle, Integer activityId, String timezone) {
        return start(userId, SessionKind.FOCUS, activityFor(userId, activityId), SessionMode.COUNTDOWN,
                cycle.getFocusMinutes() * 60, cycle, timezone);
    }

    // Stopping a session in a pomodoro cycle ends the cycle too.
    @Transactional
    public Session stop(Integer userId) {
        Instant now = now();
        Session session = settle(userId, now).orElseThrow(() -> new NotFoundException("no-running-session"));
        session.setEndedAt(now);
        if (session.getPomodoroCycle() != null) {
            session.getPomodoroCycle().setEndedAt(now);
        }
        return sessions.saveAndFlush(session);
    }

    @Transactional
    public void stopIfRunningOn(Integer userId, Integer activityId) {
        settle(userId, now())
                .filter(s -> s.getActivity() != null && s.getActivity().getId().equals(activityId))
                .ifPresent(s -> stop(userId));
    }

    @Transactional
    public Session log(Integer userId, SessionKind kind, Integer activityId, String timezone, Instant startedAt, Instant endedAt) {
        if (!endedAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("endedAt must be after startedAt");
        }
        Activity activity = null;
        if (kind == SessionKind.BREAK) {
            if (activityId != null) {
                throw new IllegalArgumentException("A break has no activity");
            }
        } else {
            if (activityId == null) {
                throw new IllegalArgumentException("A focus session needs an activity");
            }
            activity = activityFor(userId, activityId);
        }
        settle(userId, now());
        if (sessions.existsOverlapping(userId, startedAt, endedAt, NO_SESSION)) {
            throw new ConflictException("session-overlap");
        }
        Session session = new Session();
        session.setUser(users.getReferenceById(userId));
        session.setKind(kind);
        session.setActivity(activity);
        session.setSource(SessionSource.LOGGED);
        session.setTimezone(Timezones.requireRegion(timezone));
        session.setStartedAt(startedAt);
        session.setEndedAt(endedAt);
        return sessions.saveAndFlush(session);
    }

    // A null argument leaves that field as it is. A timed session stays timed after a correction.
    @Transactional
    public Session update(Integer userId, Long id, Instant startedAt, Instant endedAt, Integer activityId) {
        Session session = get(userId, id);
        if (endedAt != null && session.getEndedAt() == null) {
            throw new ConflictException("session-running");
        }
        if (activityId != null) {
            if (session.getKind() != SessionKind.FOCUS) {
                throw new IllegalArgumentException("A break has no activity");
            }
            session.setActivity(activityFor(userId, activityId));
        }
        Instant start = startedAt != null ? startedAt : session.getStartedAt();
        Instant end = endedAt != null ? endedAt : session.getEndedAt();
        if (end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("endedAt must be after startedAt");
        }
        if (sessions.existsOverlapping(userId, start, end != null ? end : FAR_FUTURE, id)) {
            throw new ConflictException("session-overlap");
        }
        session.setStartedAt(start);
        session.setEndedAt(end);
        return sessions.saveAndFlush(session);
    }

    // A pomodoro cycle left with no sessions is deleted by a database trigger.
    @Transactional
    public void delete(Integer userId, Long id) {
        sessions.delete(get(userId, id));
    }

    private Session start(Integer userId, SessionKind kind, Activity activity, SessionMode mode,
                          Integer plannedSeconds, PomodoroCycle cycle, String timezone) {
        Instant now = now();
        if (settle(userId, now).isPresent()) {
            throw new ConflictException("session-already-running");
        }
        Session session = new Session();
        session.setUser(users.getReferenceById(userId));
        session.setKind(kind);
        session.setActivity(activity);
        session.setSource(SessionSource.TIMED);
        session.setMode(mode);
        session.setPlannedSeconds(plannedSeconds);
        session.setPomodoroCycle(cycle);
        session.setTimezone(Timezones.requireRegion(timezone));
        session.setStartedAt(now);
        return sessions.saveAndFlush(session);
    }

    // Ends the running session if it is a countdown past its planned end, starts the break that follows a
    // finished pomodoro, and repeats until the running session (if any) is genuinely still running.
    private Optional<Session> settle(Integer userId, Instant now) {
        Optional<Session> open = sessions.findByUserIdAndEndedAtIsNull(userId);
        while (open.isPresent()) {
            Session session = open.get();
            Instant end = plannedEnd(session);
            if (end == null || end.isAfter(now)) {
                return open;
            }
            session.setEndedAt(end);
            sessions.saveAndFlush(session);
            startBreakAfter(session);
            open = sessions.findByUserIdAndEndedAtIsNull(userId);
        }
        return open;
    }

    private void startBreakAfter(Session ended) {
        PomodoroCycle cycle = ended.getPomodoroCycle();
        if (cycle == null || cycle.getEndedAt() != null || ended.getKind() != SessionKind.FOCUS) {
            return;
        }
        long pomodoros = sessions.countByPomodoroCycleIdAndKindAndCompletedTrue(cycle.getId(), SessionKind.FOCUS);
        int minutes = pomodoros % cycle.getPomodorosPerLongBreak() == 0
                ? cycle.getLongBreakMinutes()
                : cycle.getShortBreakMinutes();
        Session next = new Session();
        next.setUser(ended.getUser());
        next.setKind(SessionKind.BREAK);
        next.setSource(SessionSource.TIMED);
        next.setMode(SessionMode.COUNTDOWN);
        next.setPlannedSeconds(minutes * 60);
        next.setPomodoroCycle(cycle);
        next.setTimezone(ended.getTimezone());
        next.setStartedAt(ended.getEndedAt());
        sessions.saveAndFlush(next);
    }

    private Instant plannedEnd(Session session) {
        return session.getMode() == SessionMode.COUNTDOWN
                ? session.getStartedAt().plusSeconds(session.getPlannedSeconds())
                : null;
    }

    private Activity activityFor(Integer userId, Integer activityId) {
        Activity activity = activities.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new NotFoundException("not-found"));
        if (activity.getArchivedAt() != null) {
            throw new ConflictException("activity-archived");
        }
        return activity;
    }

    private static String encodeCursor(Session last) {
        String raw = last.getStartedAt() + "|" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decodeCursor(String cursor) {
        String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cursor");
        }
        return parts;
    }

    private static Instant parseCursorStart(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }

    // Whole microseconds, as Postgres stores them, so a countdown's end is exactly start + planned length.
    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
