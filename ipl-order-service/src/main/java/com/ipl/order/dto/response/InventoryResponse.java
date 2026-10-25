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
public class InventoryResponse {

    private Long id;
    private Long matchId;
    private Long ticketCategoryId;
    private String categoryName;
    private BigDecimal price;
    private Integer totalQuantity;
    private Integer availableQuantity;
}
