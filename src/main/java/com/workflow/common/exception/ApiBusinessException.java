package com.workflow.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiBusinessException extends ResponseStatusException {

    private final ApiErrorCatalog errorCatalog;

    public ApiBusinessException(HttpStatus status, ApiErrorCatalog errorCatalog, String reason) {
        super(status, reason);
        this.errorCatalog = errorCatalog;
    }

    public ApiBusinessException(HttpStatus status, ApiErrorCatalog errorCatalog, String reason, Throwable cause) {
        super(status, reason, cause);
        this.errorCatalog = errorCatalog;
    }

    public ApiErrorCatalog getErrorCatalog() {
        return errorCatalog;
    }
}
