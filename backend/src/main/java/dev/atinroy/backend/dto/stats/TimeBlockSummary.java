package dev.atinroy.backend.dto.stats;

import dev.atinroy.backend.entity.BlockPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeBlockSummary {

    private Long id;
    private BlockPurpose purpose;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationSeconds;
    private String tagLabel;
    private String todoTitle;
}
