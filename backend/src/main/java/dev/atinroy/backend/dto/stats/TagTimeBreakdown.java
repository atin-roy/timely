package dev.atinroy.backend.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagTimeBreakdown {

    private Long tagId;
    private String tagLabel;
    private String tagHexColor;
    private Long timeSeconds;
}
