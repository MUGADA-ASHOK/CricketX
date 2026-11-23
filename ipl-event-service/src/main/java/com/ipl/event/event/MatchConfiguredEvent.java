package com.ipl.event.event;

import java.time.LocalDateTime;

public record MatchConfiguredEvent(
        Long matchId,
        Long homeTeamId,
        Long homeUserId,
        String homeTeam,
        Long awayTeamId,
        Long awayUserId,
        String awayTeam,
        String stadiumName,
        LocalDateTime matchDateTime,
        int totalAllocatedSeats
) {
}
