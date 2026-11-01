package com.ipl.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnstileValidationResponse {

    private String result; // ALLOWED, DENIED
    private String message;
    private String ticketNumber;
    private Long matchId;
    private String seatNumber;
    private String stand;
    private String gate;
    private LocalDateTime scannedAt;
}

