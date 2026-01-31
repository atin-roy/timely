package dev.atinroy.backend.controller;

import dev.atinroy.backend.dto.stats.*;
import dev.atinroy.backend.security.UserDetailsImpl;
import dev.atinroy.backend.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/daily")
    public ResponseEntity<DailyStatsResponse> getDailyStats(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        DailyStatsResponse stats = statsService.getDailyStats(userDetails.getId(), targetDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/period")
    public ResponseEntity<PeriodStatsResponse> getPeriodStats(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        PeriodStatsResponse stats = statsService.getPeriodStats(userDetails.getId(), startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/lifetime")
    public ResponseEntity<LifetimeStatsResponse> getLifetimeStats(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        LifetimeStatsResponse stats = statsService.getLifetimeStats(userDetails.getId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/streak")
    public ResponseEntity<StreakResponse> getStreak(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        StreakResponse streak = statsService.getStreak(userDetails.getId());
        return ResponseEntity.ok(streak);
    }

    @GetMapping("/tags")
    public ResponseEntity<java.util.List<TagTimeBreakdown>> getTagBreakdown(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        java.util.List<TagTimeBreakdown> breakdown;
        if (date != null) {
            breakdown = statsService.getTagBreakdownForDate(userDetails.getId(), date);
        } else {
            breakdown = statsService.getTagBreakdown(userDetails.getId());
        }
        return ResponseEntity.ok(breakdown);
    }
}
