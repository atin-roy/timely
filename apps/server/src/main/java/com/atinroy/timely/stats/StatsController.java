package com.atinroy.timely.stats;

import com.atinroy.timely.api.StatsApi;
import com.atinroy.timely.api.model.DailyStatsDto;
import com.atinroy.timely.auth.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StatsController implements StatsApi {
    private final StatsService stats;

    @Override
    public ResponseEntity<List<DailyStatsDto>> getDailyStats(LocalDate from, LocalDate to) {
        return ResponseEntity.ok(stats.daily(AuthPrincipal.currentUserId(), from, to).stream()
                .map(StatsMapper::toDto)
                .toList());
    }
}
