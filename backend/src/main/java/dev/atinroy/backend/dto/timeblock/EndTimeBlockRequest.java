package dev.atinroy.backend.dto.timeblock;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndTimeBlockRequest {

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
}
