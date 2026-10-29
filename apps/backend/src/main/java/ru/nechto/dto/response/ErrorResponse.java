package ru.nechto.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ErrorDetail error) {
    public ErrorResponse(String code, String message) {
        this(new ErrorDetail(code, message, null));
    }

    public ErrorResponse(String code, String message, List<FieldErrorDetail> details) {
        this(new ErrorDetail(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(String code, String message, List<FieldErrorDetail> details) {}

    public record FieldErrorDetail(String field, String issue) {}
}
