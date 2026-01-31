package dev.atinroy.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "todos", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_tag_id", columnList = "tag_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Todo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 1000)
    private String description;

    /**
     * Optional: Tag associated with this todo.
     * NULL means the todo is not categorized.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @Column(nullable = false)
    private Boolean completed = false;

    @Column
    private Integer priority; // Optional priority (1-5, etc.)
}
