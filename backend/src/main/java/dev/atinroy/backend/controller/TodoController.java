package dev.atinroy.backend.controller;

import dev.atinroy.backend.dto.todo.TodoRequest;
import dev.atinroy.backend.dto.todo.TodoResponse;
import dev.atinroy.backend.security.UserDetailsImpl;
import dev.atinroy.backend.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public ResponseEntity<List<TodoResponse>> getAllTodos(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) Long tagId) {

        List<TodoResponse> todos;

        if (completed != null) {
            todos = todoService.getTodosByUserAndStatus(userDetails.getId(), completed);
        } else if (tagId != null) {
            todos = todoService.getTodosByUserAndTag(userDetails.getId(), tagId);
        } else {
            todos = todoService.getAllTodosByUser(userDetails.getId());
        }

        return ResponseEntity.ok(todos);
    }

    @GetMapping("/priority")
    public ResponseEntity<List<TodoResponse>> getIncompleteTodosByPriority(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<TodoResponse> todos = todoService.getIncompleteTodosOrderedByPriority(userDetails.getId());
        return ResponseEntity.ok(todos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> getTodoById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TodoResponse todo = todoService.getTodoById(id, userDetails.getId());
        return ResponseEntity.ok(todo);
    }

    @PostMapping
    public ResponseEntity<TodoResponse> createTodo(
            @Valid @RequestBody TodoRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TodoResponse todo = todoService.createTodo(request, userDetails.getId());
        return new ResponseEntity<>(todo, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> updateTodo(
            @PathVariable Long id,
            @Valid @RequestBody TodoRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TodoResponse todo = todoService.updateTodo(id, request, userDetails.getId());
        return ResponseEntity.ok(todo);
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<TodoResponse> toggleTodoCompletion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        TodoResponse todo = todoService.toggleTodoCompletion(id, userDetails.getId());
        return ResponseEntity.ok(todo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        todoService.deleteTodo(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
