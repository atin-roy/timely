package com.atinroy.timely.common;

import com.atinroy.timely.api.model.FieldErrorDto;
import com.atinroy.timely.api.model.ProblemDto;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns exceptions into the spec's problem responses.
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDto> notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getCode(), null);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDto> conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getCode(), null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ProblemDto> unauthorized(UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getCode(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDto> badArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDto> unreadable() {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDto> invalid(MethodArgumentNotValidException e) {
        ProblemDto problem = body(HttpStatus.BAD_REQUEST, "bad-request", null);
        e.getBindingResult().getFieldErrors()
                .forEach(f -> problem.addErrorsItem(new FieldErrorDto(f.getField(), f.getDefaultMessage())));
        return response(HttpStatus.BAD_REQUEST, problem);
    }

    // Query parameter bounds, like a list's limit, are checked by method validation rather than on a request body.
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDto> invalidParameter(ConstraintViolationException e) {
        ProblemDto problem = body(HttpStatus.BAD_REQUEST, "bad-request", null);
        e.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            problem.addErrorsItem(new FieldErrorDto(path.substring(path.lastIndexOf('.') + 1), v.getMessage()));
        });
        return response(HttpStatus.BAD_REQUEST, problem);
    }

    private static ResponseEntity<ProblemDto> problem(HttpStatus status, String code, String detail) {
        return response(status, body(status, code, detail));
    }

    private static ProblemDto body(HttpStatus status, String code, String detail) {
        return new ProblemDto(status.getReasonPhrase(), status.value(), code).detail(detail);
    }

    private static ResponseEntity<ProblemDto> response(HttpStatus status, ProblemDto problem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
