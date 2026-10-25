package com.ipl.event.dto.match;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateMatchRequest {

    @NotNull
    private Long homeFranchiseId;

    @NotNull
    private Long awayFranchiseId;

    @NotNull
    @Future
    private LocalDateTime matchDateTime;
}
