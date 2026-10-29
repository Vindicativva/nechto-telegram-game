package ru.nechto.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import ru.nechto.dto.response.ErrorResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Глобальный обработчик исключений API.
 * Обеспечивает единый формат ответа об ошибках для всех эндпоинтов,
 * структурирует ошибки валидации и предотвращает утечку деталей реализации и SQL клиенту.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LobbyException.class)
    public ResponseEntity<ErrorResponse> handleLobbyException(LobbyException ex, HttpServletRequest request) {
        log.warn("Lobby exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(new ErrorResponse(
                        ex.getStatus().value(),
                        request.getRequestURI(),
                        ex.getCode(),
                        ex.getMessage(),
                        List.of()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.ValidationErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.ValidationErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "VALIDATION_ERROR",
                        "Ошибка валидации входных параметров",
                        details
                ));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerValidationException(HandlerMethodValidationException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "VALIDATION_ERROR",
                        "Ошибка валидации входных параметров",
                        List.of()
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.ValidationErrorDetail> details = new ArrayList<>();
        ex.getConstraintViolations().forEach(cv -> {
            String field = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "param";
            details.add(new ErrorResponse.ValidationErrorDetail(field, cv.getMessage()));
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "VALIDATION_ERROR",
                        "Ошибка валидации входных параметров",
                        details
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        request.getRequestURI(),
                        "VALIDATION_ERROR",
                        ex.getMessage() != null ? ex.getMessage() : "Некорректные параметры запроса",
                        List.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception on {}: ", request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        request.getRequestURI(),
                        "INTERNAL_SERVER_ERROR",
                        "Внутренняя ошибка сервера. Пожалуйста, попробуйте позже.",
                        List.of()
                ));
    }
}
