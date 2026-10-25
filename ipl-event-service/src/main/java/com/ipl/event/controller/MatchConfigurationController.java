package com.ipl.event.controller;

import com.ipl.event.dto.match.MatchConfigurationRequest;
import com.ipl.event.dto.match.MatchConfigurationResponse;
import com.ipl.event.dto.response.ApiResponse;
import com.ipl.event.security.AuthenticatedUser;
import com.ipl.event.service.MatchConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/matches/{matchId}/configuration")
@RequiredArgsConstructor
public class MatchConfigurationController {

    private final MatchConfigurationService matchConfigurationService;

    /**
     * FRANCHISE only - and only the actual home franchise of this match;
     * that ownership check happens in the service, resolved from the
     * authenticated user's identity, never from a request parameter.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('FRANCHISE', 'ADMIN')")
    public ResponseEntity<ApiResponse<MatchConfigurationResponse>> createConfiguration(
            @PathVariable Long matchId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody MatchConfigurationRequest request) {
        MatchConfigurationResponse response = matchConfigurationService.createConfiguration(matchId, user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Match configured", response));
    }

    /** FRANCHISE or ADMIN, own match, before BCCI approval - see service for state rules. */
    @PutMapping
    @PreAuthorize("hasAnyRole('FRANCHISE', 'ADMIN')")
    public ResponseEntity<ApiResponse<MatchConfigurationResponse>> updateConfiguration(
            @PathVariable Long matchId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody MatchConfigurationRequest request) {
        MatchConfigurationResponse response = matchConfigurationService.updateConfiguration(matchId, user, request);
        return ResponseEntity.ok(ApiResponse.success("Match configuration updated", response));
    }

    /** Any authenticated role - e.g. ADMIN reviewing before approval, or FRANCHISE checking their own. */
    @GetMapping
    public ResponseEntity<ApiResponse<MatchConfigurationResponse>> getConfiguration(@PathVariable Long matchId) {
        MatchConfigurationResponse response = matchConfigurationService.getConfiguration(matchId);
        return ResponseEntity.ok(ApiResponse.success("Match configuration", response));
    }
}
