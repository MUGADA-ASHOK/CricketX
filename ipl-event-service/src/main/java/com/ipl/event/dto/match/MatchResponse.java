package com.ipl.event.dto.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ipl.event.enums.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {

    @JsonProperty("matchId")
    private Long matchId;

    @JsonProperty("id")
    private Long id;

    public Long getId() {
        return matchId != null ? matchId : id;
    }

    public Long getMatchId() {
        return matchId != null ? matchId : id;
    }

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
