package dev.atinroy.backend.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagRequest {

    @NotBlank(message = "Tag label is required")
    @Size(max = 50, message = "Tag label must not exceed 50 characters")
    private String label;

    @NotBlank(message = "Hex color is required")
    @Pattern(regexp = "^[0-9A-Fa-f]{6}$", message = "Hex color must be 6 characters (without #)")
    private String hexColor;
}
