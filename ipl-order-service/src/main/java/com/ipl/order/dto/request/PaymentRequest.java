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
public class PaymentRequest {

    @NotBlank(message = "Payment method is required")
    private String paymentMethod; // e.g. "RAZORPAY", "UPI", "CREDIT_CARD"

    private String paymentReference; // e.g. "PAY_REF_123456"

    private String razorpayPaymentId;

    private String razorpayOrderId;

    private String razorpaySignature;
}
