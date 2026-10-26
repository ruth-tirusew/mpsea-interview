package com.mpsesa.interview.core;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(ControllerAdvice.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return build(e.code(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ResponseEntity<ErrorResponse> validation(Exception e) {
        return build(ErrorCode.VALIDATION_FAILED, "request failed validation");
    }

    @ExceptionHandler({CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class,
            QueryTimeoutException.class, CannotCreateTransactionException.class,
            org.springframework.dao.PessimisticLockingFailureException.class})
    ResponseEntity<ErrorResponse> unavailable(Exception e) {
        log.warn("database unavailable: {}", e.getClass().getSimpleName());
        return build(ErrorCode.SERVICE_UNAVAILABLE, "service temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> other(Exception e) {
        log.error("unhandled", e);
        return build(ErrorCode.INTERNAL_SERVER_ERROR, "internal error");
    }

    private static ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), message));
    }
}
