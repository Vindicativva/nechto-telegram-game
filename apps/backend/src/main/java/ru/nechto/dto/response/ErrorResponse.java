package ru.nechto.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Единая унифицированная структура ответа с ошибкой")
public record ErrorResponse(
        @Schema(description = "Временная метка возникновения ошибки в формате ISO-8601", example = "2026-10-29T12:15:30.123Z")
        Instant timestamp,

        @Schema(description = "HTTP-статус код ответа", example = "409")
        int status,

        @Schema(description = "URL эндпоинта, вызвавшего ошибку", example = "/api/v1/rooms/X7K9M2/join")
        String path,

        @Schema(description = "Тело ошибки с машиночитаемым кодом, описанием и детализацией")
        ErrorBody error
) {
    public ErrorResponse(int status, String path, String code, String message) {
        this(Instant.now(), status, path, new ErrorBody(code, message, List.of()));
    }

    public ErrorResponse(int status, String path, String code, String message, List<ValidationErrorDetail> details) {
        this(Instant.now(), status, path, new ErrorBody(code, message, details != null ? details : List.of()));
    }

    @Schema(description = "Содержимое ошибки")
    public record ErrorBody(
            @Schema(description = "Машиночитаемый код ошибки (ROOM_FULL, VALIDATION_ERROR, NOT_FOUND и т.д.)", example = "ROOM_FULL")
            String code,

            @Schema(description = "Человекочитаемое описание ошибки", example = "Комната заполнена")
            String message,

            @Schema(description = "Список деталей валидации полей (при наличии)")
            List<ValidationErrorDetail> details
    ) {}

    @Schema(description = "Детали ошибки валидации конкретного поля")
    public record ValidationErrorDetail(
            @Schema(description = "Имя некорректного поля", example = "maxPlayers")
            String field,

            @Schema(description = "Описание проблемы валидации", example = "Количество участников должно быть в диапазоне от 4 до 12")
            String issue
    ) {}
}
