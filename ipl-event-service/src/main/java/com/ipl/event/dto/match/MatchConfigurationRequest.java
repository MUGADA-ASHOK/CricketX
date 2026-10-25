package com.ipl.event.dto.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MatchConfigurationRequest {

    @NotNull
    private Long stadiumId;

    @NotEmpty
    @Valid
    private List<TicketCategoryConfig> categories;

    @Data
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
