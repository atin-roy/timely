package com.atinroy.timely.stats;

import com.atinroy.timely.api.model.ActivityDailyStatsDto;
import com.atinroy.timely.api.model.DailyStatsDto;

public final class StatsMapper {
    private StatsMapper() {
    }

    public static DailyStatsDto toDto(StatsService.DayStats stats) {
        return new DailyStatsDto()
                .day(stats.day())
                .activities(stats.activities().stream()
                        .map(a -> new ActivityDailyStatsDto()
                                .activityId(a.activityId())
                                .totalSeconds(a.totalSeconds())
                                .totalSessions(a.totalSessions()))
                        .toList())
                .breakSeconds(stats.breakSeconds());
    }
}
