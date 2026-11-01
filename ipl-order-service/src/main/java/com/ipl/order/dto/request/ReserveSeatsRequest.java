package com.ipl.order.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveSeatsRequest {

    @NotNull(message = "Match ID is required")
    private Long matchId;

    @NotEmpty(message = "At least one specific seat must be selected")
    private List<Long> seatIds;

    private String idempotencyKey;
}

