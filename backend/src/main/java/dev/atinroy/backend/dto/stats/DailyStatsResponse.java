package dev.atinroy.backend.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatsResponse {

    private Long focusTimeSeconds;
    private Long breakTimeSeconds;
    private Long sessionCount;
    private List<TagTimeBreakdown> tagBreakdown;
    private List<TimeBlockSummary> timeline;
}
