package com.mpsesa.interview.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public record StoredResponse(int status, String body) {

    public ResponseEntity<String> toResponse() {
        return ResponseEntity.status(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
