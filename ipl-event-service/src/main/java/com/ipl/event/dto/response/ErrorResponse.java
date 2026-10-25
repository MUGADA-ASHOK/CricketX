package com.ipl.event.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ErrorResponse {

    private boolean success;
    private String message;
    private String errorCode;
    private Instant timestamp;
    private String path;

    public static ErrorResponse of(String message, String errorCode, String path) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }
}
