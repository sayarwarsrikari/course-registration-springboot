package com.example.courseregistration.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to HTTP responses so the service layer stays HTTP-free. Every error body
 * has the shape {@code {"detail": ...}} to match the FastAPI version of this service.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        CourseNotFoundException.class,
        LearnerNotFoundException.class,
        EnrollmentNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return detail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({AlreadyEnrolledException.class, AlreadyWaitlistedException.class})
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex) {
        return detail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PrerequisiteNotMetException.class)
    public ResponseEntity<Map<String, Object>> handlePrerequisiteNotMet(PrerequisiteNotMetException ex) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", ex.getMessage());
        detail.put("missing_prerequisites", ex.getMissing());
        return detail(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    /** Bean-validation failures: 422 with a list of {loc, msg, type} like FastAPI/Pydantic. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("loc", List.of("body", toSnakeCase(fe.getField())));
            err.put("msg", fe.getDefaultMessage());
            err.put("type", fe.getCode());
            errors.add(err);
        });
        return detail(HttpStatus.UNPROCESSABLE_ENTITY, errors);
    }

    /** Malformed JSON, missing body, or wrong field types: also 422, as FastAPI does. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("loc", List.of("body"));
        err.put("msg", "Request body is missing or not valid JSON for this endpoint.");
        err.put("type", "json_invalid");
        return detail(HttpStatus.UNPROCESSABLE_ENTITY, List.of(err));
    }

    private static ResponseEntity<Map<String, Object>> detail(HttpStatus status, Object detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", detail);
        return ResponseEntity.status(status).body(body);
    }

    private static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
