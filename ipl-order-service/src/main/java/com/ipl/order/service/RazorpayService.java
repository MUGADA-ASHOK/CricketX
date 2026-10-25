package com.ipl.order.service;

import com.ipl.order.config.RazorpayProperties;
import com.ipl.order.dto.response.RazorpayOrderResponse;
import com.ipl.order.entity.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayService {

    private final RazorpayProperties razorpayProperties;

    /**
     * Creates an order with Razorpay Payment Gateway.
     * Amount is converted to subunits (paise): INR 1.00 = 100 paise.
     */
    public RazorpayOrderResponse createOrder(Order order) {
        long amountInPaise = order.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValue();
        String razorpayOrderId;

        try {
            RazorpayClient client = new RazorpayClient(razorpayProperties.getKeyId(), razorpayProperties.getKeySecret());
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", razorpayProperties.getCurrency());
            orderRequest.put("receipt", order.getOrderNumber());

            com.razorpay.Order rzpOrder = client.orders.create(orderRequest);
            razorpayOrderId = rzpOrder.get("id");
            log.info("Created Razorpay Order {} for local order {}", razorpayOrderId, order.getOrderNumber());
        } catch (Exception ex) {
            log.warn("Razorpay API call failed (using fallback test Order ID): {}", ex.getMessage());
            // Generates synthetic Razorpay Order ID for offline / test mock sandbox testing
            razorpayOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }

        return RazorpayOrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .razorpayOrderId(razorpayOrderId)
                .amount(order.getTotalAmount())
                .amountInPaise(amountInPaise)
                .currency(razorpayProperties.getCurrency())
                .keyId(razorpayProperties.getKeyId())
                .userEmail(order.getUserEmail())
                .build();
    }

    /**
     * Verifies the Razorpay payment signature (HMAC-SHA256).
     */
    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        if (razorpaySignature == null || razorpaySignature.isBlank()) {
            return false;
        }
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            return Utils.verifyPaymentSignature(options, razorpayProperties.getKeySecret());
        } catch (Exception ex) {
            log.warn("Signature verification failed: {}", ex.getMessage());
            return false;
        }
    }
}
