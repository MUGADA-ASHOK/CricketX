package com.ipl.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private Long id;
    private Long ticketCategoryId;
    private String categoryName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
