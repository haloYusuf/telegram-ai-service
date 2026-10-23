package com.techno.berijalan.telegramservice.exception;

import com.techno.berijalan.telegramservice.domain.dto.response.builder.ResponseBerijalan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Handled ResponseStatusException: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseBerijalan.buildError(ex.getReason() != null ? ex.getReason() : ex.getMessage(), status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errorMessage);
        return ResponseBerijalan.buildError(errorMessage, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Handled IllegalArgumentException: {}", ex.getMessage());
        return ResponseBerijalan.buildError(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected from SSE streaming: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseBerijalan.buildError("Internal server error: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
