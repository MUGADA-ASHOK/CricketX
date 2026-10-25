package com.ipl.event.event;

import java.time.LocalDateTime;

public record MatchScheduledEvent(
        Long matchId,
        Long homeTeamId,
        String homeTeam,
        Long awayTeamId,
        String awayTeam,
        LocalDateTime matchDateTime
) {
}
