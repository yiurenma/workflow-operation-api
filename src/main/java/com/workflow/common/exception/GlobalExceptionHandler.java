package com.workflow.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleApiBusinessException(
            ApiBusinessException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ApiErrorCatalog error = exception.getErrorCatalog() != null
                ? exception.getErrorCatalog()
                : ApiErrorCatalog.byStatus(exception.getStatusCode());
        String cause = firstNonBlank(exception.getReason(), error.description());
        return buildErrorResponse(status, error, cause, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ApiErrorCatalog error = ApiErrorCatalog.byStatus(exception.getStatusCode());
        String cause = firstNonBlank(exception.getReason(), error.description());
        return buildErrorResponse(status, error, cause, request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            Exception exception,
            HttpServletRequest request) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCatalog.VALIDATION_ERROR,
                firstNonBlank(exception.getMessage(), ApiErrorCatalog.VALIDATION_ERROR.description()),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception", exception);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCatalog.INTERNAL_ERROR,
                ApiErrorCatalog.INTERNAL_ERROR.description(),
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            ApiErrorCatalog catalog,
            String cause,
            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .errorInfo(List.of(ApiErrorInfo.builder()
                        .code(catalog.code())
                        .detail(ApiErrorDetail.builder().cause(cause).build())
                        .build()))
                .requestCorrelation(firstNonBlank(
                        request.getHeader("requestCorrelation"),
                        request.getHeader("X-Request-Correlation"),
                        MDC.get("traceId")))
                .sessionCorrelation(firstNonBlank(
                        request.getHeader("sessionCorrelation"),
                        request.getHeader("X-Session-Correlation"),
                        MDC.get("spanId")))
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
