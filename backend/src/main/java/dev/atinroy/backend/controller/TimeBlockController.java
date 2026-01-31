package dev.atinroy.backend.controller;

import dev.atinroy.backend.dto.timeblock.EndTimeBlockRequest;
import dev.atinroy.backend.dto.timeblock.StartTimeBlockRequest;
import dev.atinroy.backend.dto.timeblock.TimeBlockResponse;
import dev.atinroy.backend.entity.BlockPurpose;
import dev.atinroy.backend.security.UserDetailsImpl;
import dev.atinroy.backend.service.TimeBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/timeblocks")
@RequiredArgsConstructor
public class TimeBlockController {

    private final TimeBlockService timeBlockService;

    @GetMapping("/active")
    public ResponseEntity<TimeBlockResponse> getActiveTimeBlock(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Optional<TimeBlockResponse> activeBlock = timeBlockService.getActiveTimeBlock(userDetails.getId());
        return activeBlock.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<List<TimeBlockResponse>> getTimeBlocks(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) BlockPurpose purpose,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long todoId) {

        List<TimeBlockResponse> timeBlocks;

        if (date != null) {
            timeBlocks = timeBlockService.getTimeBlocksByDate(userDetails.getId(), date);
        } else if (startDate != null && endDate != null) {
            timeBlocks = timeBlockService.getTimeBlocksByDateRange(userDetails.getId(), startDate, endDate);
        } else if (purpose != null) {
            timeBlocks = timeBlockService.getTimeBlocksByPurpose(userDetails.getId(), purpose);
        } else if (tagId != null) {
            timeBlocks = timeBlockService.getTimeBlocksByTag(userDetails.getId(), tagId);
        } else if (todoId != null) {
            timeBlocks = timeBlockService.getTimeBlocksByTodo(userDetails.getId(), todoId);
        } else {
            timeBlocks = timeBlockService.getAllTimeBlocksByUser(userDetails.getId());
        }

        return ResponseEntity.ok(timeBlocks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TimeBlockResponse> getTimeBlockById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TimeBlockResponse timeBlock = timeBlockService.getTimeBlockById(id, userDetails.getId());
        return ResponseEntity.ok(timeBlock);
    }

    @PostMapping("/start")
    public ResponseEntity<TimeBlockResponse> startTimeBlock(
            @Valid @RequestBody StartTimeBlockRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TimeBlockResponse timeBlock = timeBlockService.startTimeBlock(request, userDetails.getId());
        return new ResponseEntity<>(timeBlock, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/end")
    public ResponseEntity<TimeBlockResponse> endTimeBlock(
            @PathVariable Long id,
            @Valid @RequestBody EndTimeBlockRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TimeBlockResponse timeBlock = timeBlockService.endTimeBlock(id, request, userDetails.getId());
        return ResponseEntity.ok(timeBlock);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTimeBlock(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        timeBlockService.deleteTimeBlock(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
