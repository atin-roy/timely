package dev.atinroy.backend.dto.tag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagResponse {

    private Long id;
    private String label;
    private String hexColor;
    private Instant createdAt;
}
