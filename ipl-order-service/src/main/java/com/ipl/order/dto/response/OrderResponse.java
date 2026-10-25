package com.ipl.order.dto.response;

import com.ipl.order.entity.Order;
import com.ipl.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private Long userId;
    private String userEmail;
    private Long matchId;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String paymentReference;
    private String paymentMethod;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;

    public static OrderResponse fromEntity(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .ticketCategoryId(item.getTicketCategoryId())
                        .categoryName(item.getCategoryName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .matchId(order.getMatchId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentReference(order.getPaymentReference())
                .paymentMethod(order.getPaymentMethod())
                .razorpayOrderId(order.getRazorpayOrderId())
                .razorpayPaymentId(order.getRazorpayPaymentId())
                .expiresAt(order.getExpiresAt())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
