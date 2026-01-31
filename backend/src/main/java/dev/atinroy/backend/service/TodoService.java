package dev.atinroy.backend.service;

import dev.atinroy.backend.dto.todo.TodoRequest;
import dev.atinroy.backend.dto.todo.TodoResponse;
import dev.atinroy.backend.entity.Tag;
import dev.atinroy.backend.entity.Todo;
import dev.atinroy.backend.entity.User;
import dev.atinroy.backend.exception.ResourceNotFoundException;
import dev.atinroy.backend.exception.UnauthorizedException;
import dev.atinroy.backend.mapper.TodoMapper;
import dev.atinroy.backend.repository.TagRepository;
import dev.atinroy.backend.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;
    private final TagRepository tagRepository;
    private final UserService userService;
    private final TodoMapper todoMapper;

    public List<TodoResponse> getAllTodosByUser(Long userId) {
        return todoRepository.findByUserId(userId).stream()
                .map(todoMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TodoResponse> getTodosByUserAndStatus(Long userId, Boolean completed) {
        return todoRepository.findByUserIdAndCompleted(userId, completed).stream()
                .map(todoMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TodoResponse> getTodosByUserAndTag(Long userId, Long tagId) {
        // Verify tag belongs to user
        Tag tag = getTagAndValidateOwnership(tagId, userId);

        return todoRepository.findByUserIdAndTagId(userId, tagId).stream()
                .map(todoMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<TodoResponse> getIncompleteTodosOrderedByPriority(Long userId) {
        return todoRepository.findIncompleteTodosByUserOrderedByPriority(userId).stream()
                .map(todoMapper::toResponse)
                .collect(Collectors.toList());
    }

    public TodoResponse getTodoById(Long todoId, Long userId) {
        Todo todo = getTodoAndValidateOwnership(todoId, userId);
        return todoMapper.toResponse(todo);
    }

    @Transactional
    public TodoResponse createTodo(TodoRequest request, Long userId) {
        User user = userService.getUserById(userId);

        Todo todo = todoMapper.toEntity(request);
        todo.setUser(user);
        todo.setCompleted(false);

        // Set tag if provided
        if (request.getTagId() != null) {
            Tag tag = getTagAndValidateOwnership(request.getTagId(), userId);
            todo.setTag(tag);
        }

        Todo savedTodo = todoRepository.save(todo);
        return todoMapper.toResponse(savedTodo);
    }

    @Transactional
    public TodoResponse updateTodo(Long todoId, TodoRequest request, Long userId) {
        Todo todo = getTodoAndValidateOwnership(todoId, userId);

        // Update tag if provided
        if (request.getTagId() != null) {
            Tag tag = getTagAndValidateOwnership(request.getTagId(), userId);
            todo.setTag(tag);
        } else if (request.getTagId() == null && todo.getTag() != null) {
            // If tagId is explicitly null, remove the tag
            todo.setTag(null);
        }

        todoMapper.updateEntity(request, todo);
        Todo updatedTodo = todoRepository.save(todo);
        return todoMapper.toResponse(updatedTodo);
    }

    @Transactional
    public TodoResponse toggleTodoCompletion(Long todoId, Long userId) {
        Todo todo = getTodoAndValidateOwnership(todoId, userId);
        todo.setCompleted(!todo.getCompleted());
        Todo updatedTodo = todoRepository.save(todo);
        return todoMapper.toResponse(updatedTodo);
    }

    @Transactional
    public void deleteTodo(Long todoId, Long userId) {
        Todo todo = getTodoAndValidateOwnership(todoId, userId);
        todoRepository.delete(todo);
    }

    public long countTodosByUser(Long userId) {
        return todoRepository.countByUserId(userId);
    }

    public long countTodosByUserAndStatus(Long userId, Boolean completed) {
        return todoRepository.countByUserIdAndCompleted(userId, completed);
    }

    // Helper methods

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
