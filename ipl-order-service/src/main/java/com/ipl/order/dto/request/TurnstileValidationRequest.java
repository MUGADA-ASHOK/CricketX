package com.ipl.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnstileValidationRequest {

    @NotBlank(message = "QR Code data is required")
    private String qrCodeData;

    @NotBlank(message = "Gate name is required (e.g. Gate 3B)")
    private String gate;
}

