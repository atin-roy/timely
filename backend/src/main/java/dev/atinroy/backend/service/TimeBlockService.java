package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.timeblock.EndTimeBlockRequest;
import dev.atinroy.backend.dto.timeblock.StartTimeBlockRequest;
import dev.atinroy.backend.dto.timeblock.TimeBlockResponse;
import dev.atinroy.backend.entity.*;
import dev.atinroy.backend.exception.ResourceNotFoundException;
import dev.atinroy.backend.exception.UnauthorizedException;
import dev.atinroy.backend.exception.ValidationException;
import dev.atinroy.backend.mapper.TimeBlockMapper;
import dev.atinroy.backend.repository.TagRepository;
import dev.atinroy.backend.repository.TimeBlockRepository;
import dev.atinroy.backend.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeBlockService {

    private final TimeBlockRepository timeBlockRepository;
    private final TodoRepository todoRepository;
    private final TagRepository tagRepository;
    private final UserService userService;
    private final UserStreakService userStreakService;
    private final TimeBlockMapper timeBlockMapper;

    public Optional<TimeBlockResponse> getActiveTimeBlock(Long userId) {
        return timeBlockRepository.findByUserIdAndEndedAtIsNull(userId)
                .map(timeBlockMapper::toResponse);
    }

    public List<TimeBlockResponse> getTimeBlocksByDate(Long userId, LocalDate date) {
        return timeBlockRepository.findByUserIdAndDate(userId, date).stream()
                .map(timeBlockMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TimeBlockResponse> getTimeBlocksByDateRange(Long userId, LocalDateTime startDate,
            LocalDateTime endDate) {
        return timeBlockRepository.findByUserIdAndDateRange(userId, startDate, endDate).stream()
                .map(timeBlockMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TimeBlockResponse> getTimeBlocksByPurpose(Long userId, BlockPurpose purpose) {
        return timeBlockRepository.findByUserIdAndPurpose(userId, purpose).stream()
                .map(timeBlockMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TimeBlockResponse> getTimeBlocksByTag(Long userId, Long tagId) {
        // Validate tag ownership
        getTagAndValidateOwnership(tagId, userId);

        return timeBlockRepository.findByUserIdAndTagId(userId, tagId).stream()
                .map(timeBlockMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TimeBlockResponse> getTimeBlocksByTodo(Long userId, Long todoId) {
        // Validate todo ownership
        getTodoAndValidateOwnership(todoId, userId);

        return timeBlockRepository.findByUserIdAndTodoId(userId, todoId).stream()
                .map(timeBlockMapper::toResponse)
                .collect(Collectors.toList());
    }

    public TimeBlockResponse getTimeBlockById(Long timeBlockId, Long userId) {
        TimeBlock timeBlock = getTimeBlockAndValidateOwnership(timeBlockId, userId);
        return timeBlockMapper.toResponse(timeBlock);
    }

    @Transactional
    public TimeBlockResponse startTimeBlock(StartTimeBlockRequest request, Long userId) {
        User user = userService.getUserById(userId);

        // Check if there's already an active time block
        Optional<TimeBlock> activeBlock = timeBlockRepository.findByUserIdAndEndedAtIsNull(userId);
        if (activeBlock.isPresent()) {
            throw new ValidationException(
                    "Cannot start a new time block. Please end the current active time block first.");
        }

        // Validate mode and planned duration
        if (request.getMode() == BlockMode.TIMER && request.getPlannedDurationSeconds() == null) {
            throw new ValidationException("TIMER mode requires planned duration");
        }
        if (request.getMode() == BlockMode.STOPWATCH && request.getPlannedDurationSeconds() != null) {
            throw new ValidationException("STOPWATCH mode cannot have planned duration");
        }

        TimeBlock timeBlock = new TimeBlock();
        timeBlock.setUser(user);
        timeBlock.setPurpose(request.getPurpose());
        timeBlock.setMode(request.getMode());
        timeBlock.setStartedAt(LocalDateTime.now());
        timeBlock.setPlannedDurationSeconds(request.getPlannedDurationSeconds());

        // Validate and set todo
        if (request.getTodoId() != null) {
            if (request.getPurpose() == BlockPurpose.SHORT_BREAK || request.getPurpose() == BlockPurpose.LONG_BREAK) {
                throw new ValidationException("Break blocks cannot be associated with a todo");
            }
            Todo todo = getTodoAndValidateOwnership(request.getTodoId(), userId);
            timeBlock.setTodo(todo);

            // If todo has a tag, timeblock must use the same tag
            if (todo.getTag() != null) {
                timeBlock.setTag(todo.getTag());
            } else if (request.getTagId() != null) {
                // Todo has no tag, but request has a tag - use it
                Tag tag = getTagAndValidateOwnership(request.getTagId(), userId);
                timeBlock.setTag(tag);
            }
        } else {
            // No todo - validate tag if provided
            if (request.getTagId() != null) {
                if (request.getPurpose() == BlockPurpose.SHORT_BREAK
                        || request.getPurpose() == BlockPurpose.LONG_BREAK) {
                    throw new ValidationException("Break blocks cannot have a tag");
                }
                Tag tag = getTagAndValidateOwnership(request.getTagId(), userId);
                timeBlock.setTag(tag);
            }
        }

        TimeBlock savedTimeBlock = timeBlockRepository.save(timeBlock);
        return timeBlockMapper.toResponse(savedTimeBlock);
    }

    @Transactional
    public TimeBlockResponse endTimeBlock(Long timeBlockId, EndTimeBlockRequest request, Long userId) {
        TimeBlock timeBlock = getTimeBlockAndValidateOwnership(timeBlockId, userId);

        if (timeBlock.getEndedAt() != null) {
            throw new ValidationException("This time block has already been ended");
        }

        timeBlock.setEndedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            timeBlock.setNotes(request.getNotes());
        }

        TimeBlock savedTimeBlock = timeBlockRepository.save(timeBlock);

        // Update streak if this was a focus session
        if (timeBlock.getPurpose() == BlockPurpose.FOCUS) {
            userStreakService.updateStreakOnActivity(userId, LocalDate.now());
        }

        return timeBlockMapper.toResponse(savedTimeBlock);
    }

    @Transactional
    public void deleteTimeBlock(Long timeBlockId, Long userId) {
        TimeBlock timeBlock = getTimeBlockAndValidateOwnership(timeBlockId, userId);
        timeBlockRepository.delete(timeBlock);
    }

    // Helper methods

    private TimeBlock getTimeBlockAndValidateOwnership(Long timeBlockId, Long userId) {
        TimeBlock timeBlock = timeBlockRepository.findById(timeBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("TimeBlock", "id", timeBlockId));

        if (!timeBlock.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You don't have permission to access this time block");
        }

        return timeBlock;
    }

    private Todo getTodoAndValidateOwnership(Long todoId, Long userId) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new ResourceNotFoundException("Todo", "id", todoId));

        if (!todo.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You don't have permission to access this todo");
        }

        return todo;
    }

    private Tag getTagAndValidateOwnership(Long tagId, Long userId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", "id", tagId));

        if (!tag.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You don't have permission to access this tag");
        }

        return tag;
    }
}
