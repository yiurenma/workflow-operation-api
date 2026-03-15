package com.workflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiErrorCatalogTest {

    @Test
    void byStatusShouldReturnNotFoundForHttp404() {
        assertEquals(ApiErrorCatalog.NOT_FOUND, ApiErrorCatalog.byStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void byStatusShouldReturnConflictForHttp409() {
        assertEquals(ApiErrorCatalog.CONFLICT, ApiErrorCatalog.byStatus(HttpStatus.CONFLICT));
    }

    @Test
    void byStatusShouldReturnInternalErrorForNonStandardStatusCode() {
        assertEquals(ApiErrorCatalog.INTERNAL_ERROR, ApiErrorCatalog.byStatus(HttpStatusCode.valueOf(999)));
    }

    @Test
    void byStatusShouldReturnInternalErrorForUnmappedStatus() {
        assertEquals(ApiErrorCatalog.INTERNAL_ERROR, ApiErrorCatalog.byStatus(HttpStatus.FORBIDDEN));
    }
}
