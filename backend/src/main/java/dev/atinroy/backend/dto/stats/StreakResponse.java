package dev.atinroy.backend.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreakResponse {

    private Integer currentStreak;
    private Integer bestStreak;
    private Boolean isActive;
}
