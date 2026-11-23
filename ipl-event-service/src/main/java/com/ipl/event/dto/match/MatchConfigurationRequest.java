package com.ipl.event.dto.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MatchConfigurationRequest {

    private Long stadiumId;

    @NotEmpty
    @Valid
    private List<TicketCategoryConfig> categories;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TicketCategoryConfig {

        @NotBlank
        private String categoryName;

        @NotNull
        @Positive
        private Integer capacity;

        @NotNull
        @Positive
        private BigDecimal price;
    }
}
