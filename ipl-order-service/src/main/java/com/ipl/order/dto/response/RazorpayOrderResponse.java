package com.ipl.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderResponse {

    private Long orderId;
    private String orderNumber;
    private String razorpayOrderId;
    private BigDecimal amount;
    private Long amountInPaise;
    private String currency;
    private String keyId;
    private String userEmail;
}
