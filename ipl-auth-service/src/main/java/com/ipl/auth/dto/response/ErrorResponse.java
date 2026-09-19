package com.ipl.auth.dto.response;

import java.time.Instant;

public record ErrorResponse(
        boolean success,
        String message,
        String errorCode,
        Instant timestamp,
        String path
) {

    public static ErrorResponse of(String message, String errorCode, String path) {
        return new ErrorResponse(false, message, errorCode, Instant.now(), path);
    }
}
