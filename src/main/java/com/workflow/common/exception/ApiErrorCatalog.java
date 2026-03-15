package com.workflow.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.Arrays;

public enum ApiErrorCatalog {
    BAD_REQUEST("WF-400-000", "Bad request payload or parameters"),
    VALIDATION_ERROR("WF-400-001", "Validation failed"),
    NOT_FOUND("WF-404-000", "Requested resource was not found"),
    CONFLICT("WF-409-000", "Business conflict"),
    INTERNAL_ERROR("WF-500-000", "Internal server error");

    private final String code;
    private final String description;

    ApiErrorCatalog(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static ApiErrorCatalog byStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            return INTERNAL_ERROR;
        }
        return switch (status) {
            case BAD_REQUEST -> BAD_REQUEST;
            case NOT_FOUND -> NOT_FOUND;
            case CONFLICT -> CONFLICT;
            default -> INTERNAL_ERROR;
        };
    }

    public static String documentationText() {
        StringBuilder builder = new StringBuilder();
        Arrays.stream(values()).forEach(error ->
                builder.append("- ")
                        .append(error.code())
                        .append(": ")
                        .append(error.description())
                        .append('\n'));
        return builder.toString().trim();
    }
}
