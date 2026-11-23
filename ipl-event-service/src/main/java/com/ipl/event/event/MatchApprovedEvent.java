package com.ipl.event.event;

import java.time.LocalDateTime;

public record MatchApprovedEvent(
        Long matchId,
        Long homeTeamId,
        Long homeUserId,
        Long awayTeamId,
        Long awayUserId,
        String homeTeam,
        String awayTeam,
        String stadiumName,
        LocalDateTime matchDateTime,
        LocalDateTime ticketOpenAt
) {
}
