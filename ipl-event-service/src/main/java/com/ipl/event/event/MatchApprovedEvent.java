package com.ipl.event.event;

import java.time.LocalDateTime;

public record MatchApprovedEvent(
        Long matchId,
        Long homeTeamId,
        Long awayTeamId,
        String homeTeam,
        String awayTeam,
        LocalDateTime matchDateTime,
        LocalDateTime ticketOpenAt
) {
}
