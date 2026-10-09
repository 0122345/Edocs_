package com.edocs.common;

import java.time.Instant;

// The frontend reads `message` from every non-2xx response.
public record ErrorResponse(int status, String message, String path, Instant timestamp) {

    public static ErrorResponse of(int status, String message, String path) {
        return new ErrorResponse(status, message, path, Instant.now());
    }
}
