package com.ipl.event.dto.match;

import com.ipl.event.enums.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MatchConfigurationResponse {

    private Long matchId;
    private Long stadiumId;
    private String stadiumName;
    private MatchStatus matchStatus;
    private List<TicketCategoryView> categories;

    @Data
    @Builder
    public static class TicketCategoryView {
        private String categoryName;
        private Integer totalQuantity;
        private Integer availableQuantity;
        private BigDecimal price;
    }
}
