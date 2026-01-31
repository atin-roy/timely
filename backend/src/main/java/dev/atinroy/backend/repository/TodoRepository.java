package dev.atinroy.backend.repository;

import dev.atinroy.backend.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByUserId(Long userId);

    List<Todo> findByUserIdAndCompleted(Long userId, Boolean completed);

    List<Todo> findByUserIdAndTagId(Long userId, Long tagId);

    @Query("SELECT t FROM Todo t WHERE t.user.id = :userId AND t.completed = false ORDER BY t.priority ASC NULLS LAST, t.createdAt DESC")
    List<Todo> findIncompleteTodosByUserOrderedByPriority(@Param("userId") Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndCompleted(Long userId, Boolean completed);

    void deleteByUserId(Long userId);
}
