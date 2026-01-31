package dev.atinroy.backend.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeriodStatsResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private Long totalFocusTimeSeconds;
    private Long totalSessions;
    private Long activeDays;
    private Double averageSessionDurationSeconds;
    private LocalDate bestDay;
    private Long bestDayFocusTimeSeconds;
}
