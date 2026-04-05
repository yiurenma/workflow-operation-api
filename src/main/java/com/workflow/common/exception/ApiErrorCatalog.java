package com.workflow.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.Arrays;

public enum ApiErrorCatalog {
    // Generic fallback codes
    BAD_REQUEST("WF-400-000", "Bad request payload or parameters"),
    VALIDATION_ERROR("WF-400-001", "Validation failed"),
    NOT_FOUND("WF-404-000", "Requested resource was not found"),
    CONFLICT("WF-409-000", "Business conflict"),
    INTERNAL_ERROR("WF-500-000", "Internal server error"),

    // Workflow query/update/delete
    WORKFLOW_APP_NOT_UNIQUE("WF-400-101", "Application name must exist exactly once"),
    WORKFLOW_BODY_REQUIRED("WF-400-102", "Workflow body is required for update operation"),
    WORKFLOW_UPDATE_DUPLICATE_KEY("WF-409-101", "Workflow update failed after duplicate key retries"),
    WORKFLOW_DELETE_REPORT_EXISTS("WF-409-201", "Cannot delete workflow when reports exist"),
    WORKFLOW_MAPPING_LINKING_ID_INVALID("WF-400-202", "Workflow mapping has null or blank linkingId"),

    // Workflow auto copy
    AUTOCOPY_SOURCE_TARGET_SAME("WF-400-301", "Source and target application names must be different"),
    AUTOCOPY_SOURCE_NOT_UNIQUE("WF-400-302", "Source application name must exist exactly once"),
    AUTOCOPY_TARGET_TOO_MANY("WF-400-303", "Target application name must exist at most once"),

    // Entity setting query/history/update
    ENTITY_SETTING_HISTORY_APP_NOT_UNIQUE("WF-400-401", "History query requires application name that exists exactly once"),
    ENTITY_SETTING_NOT_FOUND("WF-404-101", "Entity setting not found for the given application name"),
    ENTITY_SETTING_APP_NOT_UNIQUE("WF-400-402", "Entity setting applicationName must exist exactly once");

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
