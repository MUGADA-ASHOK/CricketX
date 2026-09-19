package com.ipl.event.dto.ticket;

import com.ipl.event.enums.TicketCategoryStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TicketCategoryResponse {

    private Long id;

    private Long stadiumId;

    private String categoryName;

    private String description;

    private BigDecimal price;

    private Integer capacity;

    private Integer availableQuantity;

    private TicketCategoryStatus status;
}
