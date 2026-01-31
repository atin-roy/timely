package dev.atinroy.backend.dto.todo;

import dev.atinroy.backend.dto.tag.TagResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoResponse {

    private Long id;
    private String title;
    private String description;
    private TagResponse tag;
    private Boolean completed;
    private Integer priority;
    private Instant createdAt;
    private Instant updatedAt;
}
