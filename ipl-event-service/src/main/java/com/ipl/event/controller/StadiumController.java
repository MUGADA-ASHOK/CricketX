package com.ipl.event.controller;

import com.ipl.event.dto.response.ApiResponse;
import com.ipl.event.dto.stadium.CreateStadiumRequest;
import com.ipl.event.dto.stadium.StadiumResponse;
import com.ipl.event.service.StadiumService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/stadiums")
@RequiredArgsConstructor
public class StadiumController {

    private final StadiumService stadiumService;

    /** BCCI Admin registers a new stadium. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StadiumResponse>> createStadium(@Valid @RequestBody CreateStadiumRequest request) {
        StadiumResponse response = stadiumService.createStadium(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stadium registered successfully", response));
    }

    /** Browse all stadiums (authenticated users/fans/franchise). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StadiumResponse>>> listStadiums() {
        return ResponseEntity.ok(ApiResponse.success("Stadiums list", stadiumService.listStadiums()));
    }

    /** Get stadium details. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StadiumResponse>> getStadium(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Stadium details", stadiumService.getStadium(id)));
    }
}
