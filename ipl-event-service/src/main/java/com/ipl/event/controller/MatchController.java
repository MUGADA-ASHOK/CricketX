package com.ipl.event.controller;

import com.ipl.event.dto.match.CreateMatchRequest;
import com.ipl.event.dto.match.MatchResponse;
import com.ipl.event.dto.response.ApiResponse;
import com.ipl.event.enums.MatchStatus;
import com.ipl.event.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /** ADMIN only - see SecurityConfig for the coarse URL rule matching this. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MatchResponse>> createMatch(@Valid @RequestBody CreateMatchRequest request) {
        MatchResponse match = matchService.createMatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Match scheduled", match));
    }

    /** ADMIN only. */
    @PostMapping("/{matchId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MatchResponse>> approveMatch(@PathVariable Long matchId) {
        MatchResponse match = matchService.approveMatch(matchId);
        return ResponseEntity.ok(ApiResponse.success("Match approved", match));
    }

    /** Any authenticated role (USER/FRANCHISE/ADMIN) - browsing matches. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<MatchResponse>>> listMatches(
            @RequestParam(required = false) MatchStatus status) {
        List<MatchResponse> matches = matchService.listMatches(status);
        return ResponseEntity.ok(ApiResponse.success("Matches", matches));
    }

    /** Any authenticated role. */
    @GetMapping("/{matchId}")
    public ResponseEntity<ApiResponse<MatchResponse>> getMatch(@PathVariable Long matchId) {
        MatchResponse match = matchService.getMatch(matchId);
        return ResponseEntity.ok(ApiResponse.success("Match", match));
    }
}
