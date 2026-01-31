package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "tags")
public class Tag extends BaseEntity {
    @Column(name = "tag_label", nullable = false, length = 50)
    private String label;

    @Column(name="tag_hex_color", nullable = false, length = 6)
    private String hexColor;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
