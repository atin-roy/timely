package dev.atinroy.backend.dto.timeblock;

import dev.atinroy.backend.entity.BlockMode;
import dev.atinroy.backend.entity.BlockPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartTimeBlockRequest {

    @NotNull(message = "Purpose is required")
    private BlockPurpose purpose;

    @NotNull(message = "Mode is required")
    private BlockMode mode;

    private Long todoId;

    private Long tagId;

    @Positive(message = "Planned duration must be positive")
    private Long plannedDurationSeconds;
}
