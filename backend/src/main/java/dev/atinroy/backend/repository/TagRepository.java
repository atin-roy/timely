package dev.atinroy.backend.repository;

import dev.atinroy.backend.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByUserId(Long userId);

    Optional<Tag> findByUserIdAndLabel(Long userId, String label);

    boolean existsByUserIdAndLabel(Long userId, String label);

    void deleteByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
