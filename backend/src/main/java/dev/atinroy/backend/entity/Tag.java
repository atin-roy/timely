package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tags")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class Tag extends BaseEntity {
    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "tag_label", nullable = false, length = 50)
    private String label;

    @Column(name="tag_hex_color", nullable = false, length = 6)
    private String hexColor;

    @EqualsAndHashCode.Include
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
