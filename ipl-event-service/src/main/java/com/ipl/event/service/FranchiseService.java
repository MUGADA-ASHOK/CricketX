package com.ipl.event.service;

import com.ipl.event.dto.franchise.CreateFranchiseRequest;
import com.ipl.event.dto.franchise.FranchiseResponse;
import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Stadium;
import com.ipl.event.enums.FranchiseStatus;
import com.ipl.event.exception.FranchiseNotFoundException;
import com.ipl.event.exception.StadiumNotFoundException;
import com.ipl.event.repository.FranchiseRepository;
import com.ipl.event.repository.StadiumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FranchiseService {

    private final FranchiseRepository franchiseRepository;
    private final StadiumRepository stadiumRepository;

    @Transactional
    public FranchiseResponse createFranchise(CreateFranchiseRequest request) {
        if (franchiseRepository.existsByFranchiseName(request.getFranchiseName())) {
            throw new IllegalArgumentException("Franchise name '" + request.getFranchiseName() + "' is already registered");
        }
        if (franchiseRepository.findByTeamCode(request.getTeamCode()).isPresent()) {
            throw new IllegalArgumentException("Team code '" + request.getTeamCode() + "' is already taken");
        }
        if (franchiseRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new IllegalArgumentException("A franchise is already linked to userId " + request.getUserId());
        }

        Stadium homeStadium = null;
        if (request.getHomeStadiumId() != null) {
            homeStadium = stadiumRepository.findById(request.getHomeStadiumId())
                    .orElseThrow(() -> new StadiumNotFoundException("Stadium not found: " + request.getHomeStadiumId()));
        }

        Franchise franchise = Franchise.builder()
                .userId(request.getUserId())
                .franchiseName(request.getFranchiseName())
                .teamCode(request.getTeamCode().toUpperCase())
                .shortName(request.getShortName() != null ? request.getShortName() : request.getFranchiseName())
                .city(request.getCity())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .homeStadium(homeStadium)
                .status(FranchiseStatus.ACTIVE)
                .build();

        Franchise saved = franchiseRepository.save(franchise);
        log.info("Registered franchise: {} (code={}, ID={})", saved.getFranchiseName(), saved.getTeamCode(), saved.getId());
        return FranchiseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<FranchiseResponse> listFranchises() {
        return franchiseRepository.findAll().stream()
                .map(FranchiseResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public FranchiseResponse getFranchise(Long id) {
        Franchise franchise = franchiseRepository.findById(id)
                .orElseThrow(() -> new FranchiseNotFoundException("Franchise not found: " + id));
        return FranchiseResponse.fromEntity(franchise);
    }
}
