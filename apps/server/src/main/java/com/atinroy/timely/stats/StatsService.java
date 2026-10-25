package com.atinroy.timely.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {
    private static final int MAX_DAYS = 366;

    public record ActivityStats(Integer activityId, long totalSeconds, int totalSessions) {
    }

    public record DayStats(LocalDate day, List<ActivityStats> activities, long breakSeconds) {
    }

    private record Row(LocalDate day, String kind, Integer activityId, long totalSeconds, int totalSessions) {
    }

    private final JdbcTemplate jdbc;

    // Reads the daily_stats view. Days with no sessions are left out.
    public List<DayStats> daily(Integer userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new IllegalArgumentException("to must be on or after from, and at most " + MAX_DAYS + " days later");
        }
        List<Row> rows = jdbc.query("""
                        select day, kind, activity_id, total_seconds, total_sessions
                        from daily_stats
                        where user_id = ? and day between ? and ?
                        order by day, activity_id
                        """,
                (rs, i) -> new Row(rs.getObject("day", LocalDate.class), rs.getString("kind"),
                        rs.getObject("activity_id", Integer.class), rs.getLong("total_seconds"), rs.getInt("total_sessions")),
                userId, from, to);
        return rows.stream()
                .collect(Collectors.groupingBy(Row::day, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(day -> toDay(day.getKey(), day.getValue()))
                .toList();
    }

    private static DayStats toDay(LocalDate day, List<Row> rows) {
        List<ActivityStats> activities = rows.stream()
                .filter(r -> r.kind().equals("focus"))
                .map(r -> new ActivityStats(r.activityId(), r.totalSeconds(), r.totalSessions()))
                .toList();
        long breakSeconds = rows.stream()
                .filter(r -> r.kind().equals("break"))
                .mapToLong(Row::totalSeconds)
                .sum();
        return new DayStats(day, activities, breakSeconds);
    }
}
