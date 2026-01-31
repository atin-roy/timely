package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.stats.*;
import dev.atinroy.backend.entity.BlockPurpose;
import dev.atinroy.backend.entity.TimeBlock;
import dev.atinroy.backend.repository.TagRepository;
import dev.atinroy.backend.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final TimeBlockRepository timeBlockRepository;
    private final TagRepository tagRepository;
    private final UserStreakService userStreakService;

    public DailyStatsResponse getDailyStats(Long userId, LocalDate date) {
        List<TimeBlock> timeBlocks = timeBlockRepository.findByUserIdAndDate(userId, date);

        // Calculate focus time
        long focusTimeSeconds = timeBlocks.stream()
                .filter(tb -> tb.getPurpose() == BlockPurpose.FOCUS && tb.getActualDurationSeconds() != null)
                .mapToLong(TimeBlock::getActualDurationSeconds)
                .sum();

        // Calculate break time
        long breakTimeSeconds = timeBlocks.stream()
                .filter(tb -> (tb.getPurpose() == BlockPurpose.SHORT_BREAK ||
                        tb.getPurpose() == BlockPurpose.LONG_BREAK) &&
                        tb.getActualDurationSeconds() != null)
                .mapToLong(TimeBlock::getActualDurationSeconds)
                .sum();

        // Count focus sessions
        long sessionCount = timeBlocks.stream()
                .filter(tb -> tb.getPurpose() == BlockPurpose.FOCUS && tb.getEndedAt() != null)
                .count();

        // Get tag breakdown
        List<TagTimeBreakdown> tagBreakdown = getTagBreakdownForDate(userId, date);

        // Create timeline
        List<TimeBlockSummary> timeline = timeBlocks.stream()
                .filter(tb -> tb.getEndedAt() != null)
                .map(this::mapToTimeBlockSummary)
                .sorted(Comparator.comparing(TimeBlockSummary::getStartedAt))
                .collect(Collectors.toList());

        return new DailyStatsResponse(focusTimeSeconds, breakTimeSeconds, sessionCount, tagBreakdown, timeline);
    }

    public PeriodStatsResponse getPeriodStats(Long userId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<TimeBlock> timeBlocks = timeBlockRepository.findByUserIdAndDateRange(userId, startDateTime, endDateTime);

        // Calculate total focus time
        long totalFocusTimeSeconds = timeBlocks.stream()
                .filter(tb -> tb.getPurpose() == BlockPurpose.FOCUS && tb.getActualDurationSeconds() != null)
                .mapToLong(TimeBlock::getActualDurationSeconds)
                .sum();

        // Count total sessions
        long totalSessions = timeBlocks.stream()
                .filter(tb -> tb.getPurpose() == BlockPurpose.FOCUS && tb.getEndedAt() != null)
                .count();

        // Count active days (days with at least one completed focus session)
        long activeDays = timeBlocks.stream()
                .filter(tb -> tb.getPurpose() == BlockPurpose.FOCUS && tb.getEndedAt() != null)
                .map(tb -> tb.getStartedAt().toLocalDate())
                .distinct()
                .count();

        // Calculate average session duration
        Double averageSessionDuration = totalSessions > 0 ? (double) totalFocusTimeSeconds / totalSessions : 0.0;

        // Find best day
        Map<LocalDate, Long> dailyFocusTime = timeBlocks.stream()
                .filter(tb -> tb.getPurpose() == BlockPurpose.FOCUS && tb.getActualDurationSeconds() != null)
                .collect(Collectors.groupingBy(
                        tb -> tb.getStartedAt().toLocalDate(),
                        Collectors.summingLong(TimeBlock::getActualDurationSeconds)));

        LocalDate bestDay = null;
        Long bestDayFocusTime = 0L;
        for (Map.Entry<LocalDate, Long> entry : dailyFocusTime.entrySet()) {
            if (entry.getValue() > bestDayFocusTime) {
                bestDay = entry.getKey();
                bestDayFocusTime = entry.getValue();
            }
        }

        return new PeriodStatsResponse(
                startDate,
                endDate,
                totalFocusTimeSeconds,
                totalSessions,
                activeDays,
                averageSessionDuration,
                bestDay,
                bestDayFocusTime);
    }

    public LifetimeStatsResponse getLifetimeStats(Long userId) {
        Long totalFocusTimeSeconds = timeBlockRepository.getTotalFocusTimeByUserId(userId);
        Long totalSessions = timeBlockRepository.countFocusSessionsByUserId(userId);

        // Get total active days
        List<LocalDate> activeDates = timeBlockRepository.getDistinctFocusDates(userId);
        long totalActiveDays = activeDates.size();

        // Get average session duration
        Double averageSessionDuration = timeBlockRepository.getAverageFocusSessionDuration(userId);
        if (averageSessionDuration == null) {
            averageSessionDuration = 0.0;
        }

        return new LifetimeStatsResponse(
                totalFocusTimeSeconds,
                totalSessions,
                totalActiveDays,
                averageSessionDuration);
    }

    public List<TagTimeBreakdown> getTagBreakdown(Long userId) {
        List<Object[]> results = timeBlockRepository.getFocusTimeByTag(userId);
        return results.stream()
                .map(this::mapToTagTimeBreakdown)
                .collect(Collectors.toList());
    }

    public List<TagTimeBreakdown> getTagBreakdownForDate(Long userId, LocalDate date) {
        List<Object[]> results = timeBlockRepository.getFocusTimeByTagAndDate(userId, date);
        return results.stream()
                .map(this::mapToTagTimeBreakdown)
                .collect(Collectors.toList());
    }

    public StreakResponse getStreak(Long userId) {
        return userStreakService.getStreakByUser(userId);
    }

    // Helper methods

    private TimeBlockSummary mapToTimeBlockSummary(TimeBlock timeBlock) {
        return new TimeBlockSummary(
                timeBlock.getId(),
                timeBlock.getPurpose(),
                timeBlock.getStartedAt(),
                timeBlock.getEndedAt(),
                timeBlock.getActualDurationSeconds(),
                timeBlock.getTag() != null ? timeBlock.getTag().getLabel() : null,
                timeBlock.getTodo() != null ? timeBlock.getTodo().getTitle() : null);
    }

    private TagTimeBreakdown mapToTagTimeBreakdown(Object[] result) {
        Long tagId = ((Number) result[0]).longValue();
        String tagLabel = (String) result[1];
        Long timeSeconds = ((Number) result[2]).longValue();

        // Get tag color from repository
        String hexColor = tagRepository.findById(tagId)
                .map(tag -> tag.getHexColor())
                .orElse("000000");

        return new TagTimeBreakdown(tagId, tagLabel, hexColor, timeSeconds);
    }
}
