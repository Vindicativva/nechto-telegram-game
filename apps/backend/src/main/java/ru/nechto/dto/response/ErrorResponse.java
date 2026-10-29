package ru.nechto.dto.response;

public record ErrorResponse(ErrorDetail error) {
    public ErrorResponse(String code, String message) {
        this(new ErrorDetail(code, message));
    }

    public record ErrorDetail(String code, String message) {}
}
