package com.ipl.event.controller;

import com.ipl.event.dto.franchise.CreateFranchiseRequest;
import com.ipl.event.dto.franchise.FranchiseResponse;
import com.ipl.event.dto.response.ApiResponse;
import com.ipl.event.service.FranchiseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/franchises")
@RequiredArgsConstructor
public class FranchiseController {

    private final FranchiseService franchiseService;

    /** BCCI Admin registers a new IPL Franchise. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FranchiseResponse>> createFranchise(@Valid @RequestBody CreateFranchiseRequest request) {
        FranchiseResponse response = franchiseService.createFranchise(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Franchise registered successfully", response));
    }

    /** Browse all franchises (any authenticated role). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FranchiseResponse>>> listFranchises() {
        return ResponseEntity.ok(ApiResponse.success("Franchises list", franchiseService.listFranchises()));
    }

    /** Get franchise details by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FranchiseResponse>> getFranchise(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Franchise details", franchiseService.getFranchise(id)));
    }
}
