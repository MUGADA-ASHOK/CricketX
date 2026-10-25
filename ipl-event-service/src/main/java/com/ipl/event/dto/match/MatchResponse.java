package com.ipl.event.dto.match;

import com.ipl.event.enums.MatchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MatchResponse {

    private Long matchId;

    private Long homeFranchiseId;
    private String homeTeamName;
    private String homeTeamCode;

    private Long awayFranchiseId;
    private String awayTeamName;
    private String awayTeamCode;

    private Long stadiumId;
    private String stadiumName;

    private LocalDateTime matchDateTime;
    private MatchStatus status;
    private LocalDateTime approvedAt;
    private LocalDateTime ticketOpenAt;
}
