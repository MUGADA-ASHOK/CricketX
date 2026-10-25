package com.ipl.event.event;

import java.time.LocalDateTime;

public record MatchTicketsOpenedEvent(
        Long matchId,
        Long homeFranchiseId,
        Long awayFranchiseId,
        LocalDateTime matchDateTime,
        LocalDateTime ticketOpenAt
) {}
