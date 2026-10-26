package com.mpsesa.interview.core;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYOUT_NOT_FOUND(HttpStatus.NOT_FOUND),
    BATCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT),
    ALREADY_REVERSED(HttpStatus.CONFLICT),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    WALLET_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    SAME_WALLET(HttpStatus.UNPROCESSABLE_ENTITY),
    NOT_REVERSIBLE(HttpStatus.UNPROCESSABLE_ENTITY),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
