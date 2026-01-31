package dev.atinroy.backend.repository;

import dev.atinroy.backend.entity.BlockPurpose;
import dev.atinroy.backend.entity.TimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {

    List<TimeBlock> findByUserId(Long userId);

    Optional<TimeBlock> findByUserIdAndEndedAtIsNull(Long userId);

    @Query("SELECT tb FROM TimeBlock tb WHERE tb.user.id = :userId AND DATE(tb.startedAt) = :date ORDER BY tb.startedAt ASC")
    List<TimeBlock> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT tb FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.startedAt BETWEEN :startDate AND :endDate ORDER BY tb.startedAt ASC")
    List<TimeBlock> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<TimeBlock> findByUserIdAndPurpose(Long userId, BlockPurpose purpose);

    List<TimeBlock> findByUserIdAndTagId(Long userId, Long tagId);

    List<TimeBlock> findByUserIdAndTodoId(Long userId, Long todoId);

    @Query("SELECT COALESCE(SUM(tb.actualDurationSeconds), 0) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.endedAt IS NOT NULL")
    Long getTotalFocusTimeByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(tb.actualDurationSeconds), 0) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND DATE(tb.startedAt) = :date AND tb.endedAt IS NOT NULL")
    Long getTotalFocusTimeByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(tb.actualDurationSeconds), 0) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.startedAt BETWEEN :startDate AND :endDate AND tb.endedAt IS NOT NULL")
    Long getTotalFocusTimeByUserIdAndDateRange(@Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(tb) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.endedAt IS NOT NULL")
    Long countFocusSessionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(tb) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND DATE(tb.startedAt) = :date AND tb.endedAt IS NOT NULL")
    Long countFocusSessionsByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT tb.tag.id, tb.tag.label, SUM(tb.actualDurationSeconds) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.tag IS NOT NULL AND tb.endedAt IS NOT NULL GROUP BY tb.tag.id, tb.tag.label")
    List<Object[]> getFocusTimeByTag(@Param("userId") Long userId);

    @Query("SELECT tb.tag.id, tb.tag.label, SUM(tb.actualDurationSeconds) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.tag IS NOT NULL AND DATE(tb.startedAt) = :date AND tb.endedAt IS NOT NULL GROUP BY tb.tag.id, tb.tag.label")
    List<Object[]> getFocusTimeByTagAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT DISTINCT DATE(tb.startedAt) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.endedAt IS NOT NULL ORDER BY DATE(tb.startedAt) ASC")
    List<LocalDate> getDistinctFocusDates(@Param("userId") Long userId);

    @Query("SELECT AVG(tb.actualDurationSeconds) FROM TimeBlock tb WHERE tb.user.id = :userId AND tb.purpose = 'FOCUS' AND tb.endedAt IS NOT NULL")
    Double getAverageFocusSessionDuration(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
