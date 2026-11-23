package com.ipl.event.dto.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipl.event.enums.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchConfigurationResponse {

    @JsonProperty("matchId")
    private Long matchId;

    @JsonProperty("id")
    private Long id;

    public Long getId() {
        return matchId != null ? matchId : id;
    }

    public Long getMatchId() {
        return matchId != null ? matchId : id;
    }

    private Long stadiumId;
    private String stadiumName;
    private MatchStatus matchStatus;
    private List<TicketCategoryView> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketCategoryView {
        private String categoryName;
        private Integer totalQuantity;
        private Integer availableQuantity;
        private BigDecimal price;
    }
}
