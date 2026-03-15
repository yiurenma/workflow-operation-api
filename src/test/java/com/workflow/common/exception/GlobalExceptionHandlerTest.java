package com.workflow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldBuildBusinessErrorFromResponseStatusException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("requestCorrelation", "REQ-1");
        request.addHeader("sessionCorrelation", "SES-1");

        ResponseStatusException exception = new ResponseStatusException(HttpStatus.CONFLICT, "conflict detail");
        var response = handler.handleResponseStatusException(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("WF-409-000", response.getBody().getErrorInfo().get(0).getCode());
        assertEquals("conflict detail", response.getBody().getErrorInfo().get(0).getDetail().getCause());
        assertEquals("REQ-1", response.getBody().getRequestCorrelation());
        assertEquals("SES-1", response.getBody().getSessionCorrelation());
    }

    @Test
    void shouldBuildScenarioSpecificCodeFromApiBusinessException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiBusinessException exception = new ApiBusinessException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCatalog.WORKFLOW_APP_NOT_UNIQUE,
                "Application name must exist exactly once; found: 2"
        );

        var response = handler.handleApiBusinessException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("WF-400-101", response.getBody().getErrorInfo().get(0).getCode());
        assertEquals("Application name must exist exactly once; found: 2",
                response.getBody().getErrorInfo().get(0).getDetail().getCause());
    }

    @Test
    void shouldBuildValidationError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("applicationName", "String");

        var response = handler.handleValidationException(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("WF-400-001", response.getBody().getErrorInfo().get(0).getCode());
    }

    @Test
    void shouldBuildSystemErrorForUnhandledException() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = handler.handleException(new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("WF-500-000", response.getBody().getErrorInfo().get(0).getCode());
        assertEquals("Internal server error", response.getBody().getErrorInfo().get(0).getDetail().getCause());
    }
}
