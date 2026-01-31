package dev.atinroy.backend.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LifetimeStatsResponse {

    private Long totalFocusTimeSeconds;
    private Long totalSessions;
    private Long totalActiveDays;
    private Double averageSessionDurationSeconds;
}
