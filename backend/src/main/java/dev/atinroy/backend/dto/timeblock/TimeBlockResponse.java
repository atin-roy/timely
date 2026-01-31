package dev.atinroy.backend.dto.timeblock;

import dev.atinroy.backend.dto.tag.TagResponse;
import dev.atinroy.backend.dto.todo.TodoResponse;
import dev.atinroy.backend.entity.BlockMode;
import dev.atinroy.backend.entity.BlockPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeBlockResponse {

    private Long id;
    private TodoResponse todo;
    private TagResponse tag;
    private BlockPurpose purpose;
    private BlockMode mode;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long plannedDurationSeconds;
    private Long actualDurationSeconds;
    private Boolean completed;
    private String notes;
    private Boolean active;
    private Long currentDurationSeconds;
    private Long remainingSeconds;
}
