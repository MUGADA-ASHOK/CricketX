package com.ipl.event.service;

import com.ipl.event.dto.match.MatchConfigurationRequest;
import com.ipl.event.dto.match.MatchConfigurationResponse;
import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Match;
import com.ipl.event.entity.MatchTicketInventory;
import com.ipl.event.entity.Stadium;
import com.ipl.event.entity.TicketCategory;
import com.ipl.event.enums.MatchStatus;
import com.ipl.event.enums.TicketCategoryStatus;
import com.ipl.event.exception.ConfigurationAlreadyExistsException;
import com.ipl.event.exception.FranchiseNotFoundException;
import com.ipl.event.exception.InvalidMatchStateException;
import com.ipl.event.exception.InvalidTicketConfigurationException;
import com.ipl.event.exception.StadiumNotFoundException;
import com.ipl.event.exception.UnauthorizedFranchiseException;
import com.ipl.event.repository.FranchiseRepository;
import com.ipl.event.repository.MatchRepository;
import com.ipl.event.repository.MatchTicketInventoryRepository;
import com.ipl.event.repository.StadiumRepository;
import com.ipl.event.repository.TicketCategoryRepository;
import com.ipl.event.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Franchise-side match configuration: stadium + match-specific ticket
 * categories (MatchTicketInventory). Ownership is always resolved from the
 * AUTHENTICATED user's identity (Franchise.userId), never from a
 * client-supplied franchise/team ID - see AuthenticatedUser.
 */
@Service
@RequiredArgsConstructor
public class MatchConfigurationService {

    private final MatchRepository matchRepository;
    private final FranchiseRepository franchiseRepository;
    private final StadiumRepository stadiumRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final MatchTicketInventoryRepository matchTicketInventoryRepository;

    @Transactional
    public MatchConfigurationResponse createConfiguration(Long matchId, AuthenticatedUser user, MatchConfigurationRequest request) {
        Match match = getOwnedMatchOrThrow(matchId, user);

        if (match.getStatus() != MatchStatus.CONFIGURATION_PENDING) {
            throw new InvalidMatchStateException(
                    "Match must be CONFIGURATION_PENDING to configure - current status: " + match.getStatus());
        }
        if (matchTicketInventoryRepository.existsByMatchId(matchId)) {
            throw new ConfigurationAlreadyExistsException("Match already configured - use PUT to update before approval");
        }

        applyConfiguration(match, request);
        match.setStatus(MatchStatus.WAITING_FOR_APPROVAL);
        matchRepository.save(match);

        return toResponse(match);
    }

    @Transactional
    public MatchConfigurationResponse updateConfiguration(Long matchId, AuthenticatedUser user, MatchConfigurationRequest request) {
        Match match = getOwnedMatchOrThrow(matchId, user);

        if (match.getStatus() != MatchStatus.WAITING_FOR_APPROVAL) {
            throw new InvalidMatchStateException(
                    "Configuration can only be updated before BCCI approval - current status: " + match.getStatus());
        }

        // Replace-all semantics: simplest correct behavior for "update before approval".
        matchTicketInventoryRepository.deleteAll(matchTicketInventoryRepository.findByMatchId(matchId));
        applyConfiguration(match, request);
        matchRepository.save(match);

        return toResponse(match);
    }

    @Transactional(readOnly = true)
    public MatchConfigurationResponse getConfiguration(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new com.ipl.event.exception.MatchNotFoundException("Match not found: " + matchId));
        return toResponse(match);
    }

    private void applyConfiguration(Match match, MatchConfigurationRequest request) {
        Stadium stadium = stadiumRepository.findById(request.getStadiumId())
                .orElseThrow(() -> new StadiumNotFoundException("Stadium not found: " + request.getStadiumId()));

        int totalRequestedCapacity = request.getCategories().stream()
                .mapToInt(MatchConfigurationRequest.TicketCategoryConfig::getCapacity)
                .sum();
        if (totalRequestedCapacity > stadium.getCapacity()) {
            throw new InvalidTicketConfigurationException(
                    "Total ticket category capacity (" + totalRequestedCapacity
                            + ") exceeds stadium capacity (" + stadium.getCapacity() + ")");
        }

        for (MatchConfigurationRequest.TicketCategoryConfig categoryConfig : request.getCategories()) {
            TicketCategory ticketCategory = ticketCategoryRepository
                    .findByStadiumId(stadium.getId()).stream()
                    .filter(tc -> tc.getCategoryName().equalsIgnoreCase(categoryConfig.getCategoryName()))
                    .findFirst()
                    .orElseGet(() -> ticketCategoryRepository.save(TicketCategory.builder()
                            .stadium(stadium)
                            .categoryName(categoryConfig.getCategoryName())
                            .price(categoryConfig.getPrice())
                            .capacity(categoryConfig.getCapacity())
                            .availableQuantity(categoryConfig.getCapacity())
                            .status(TicketCategoryStatus.ACTIVE)
                            .build()));

            MatchTicketInventory inventory = MatchTicketInventory.builder()
                    .matchId(match.getId())
                    .ticketCategory(ticketCategory)
                    .totalQuantity(categoryConfig.getCapacity())
                    .availableQuantity(categoryConfig.getCapacity())
                    .price(categoryConfig.getPrice())
                    .build();
            matchTicketInventoryRepository.save(inventory);
        }

        match.setStadium(stadium);
    }

    /**
     * Resolves the match AND verifies the authenticated user's own
     * franchise is its home franchise. Never trust a franchise/team ID
     * from the request body for this check.
     */
    private Match getOwnedMatchOrThrow(Long matchId, AuthenticatedUser user) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new com.ipl.event.exception.MatchNotFoundException("Match not found: " + matchId));

        if ("ADMIN".equalsIgnoreCase(user.role())) {
            return match;
        }

        Franchise franchise = franchiseRepository.findByUserId(user.userId())
                .orElseThrow(() -> new FranchiseNotFoundException("No franchise is registered for this account"));

        if (!franchise.getId().equals(match.getHomeFranchise().getId())) {
            throw new UnauthorizedFranchiseException("Only the home franchise can configure this match");
        }

        return match;
    }

    private MatchConfigurationResponse toResponse(Match match) {
        List<MatchTicketInventory> inventory = matchTicketInventoryRepository.findByMatchId(match.getId());

        List<MatchConfigurationResponse.TicketCategoryView> categoryViews = inventory.stream()
                .map(item -> MatchConfigurationResponse.TicketCategoryView.builder()
                        .categoryName(item.getTicketCategory().getCategoryName())
                        .totalQuantity(item.getTotalQuantity())
                        .availableQuantity(item.getAvailableQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();

        return MatchConfigurationResponse.builder()
                .matchId(match.getId())
                .stadiumId(match.getStadium() != null ? match.getStadium().getId() : null)
                .stadiumName(match.getStadium() != null ? match.getStadium().getName() : null)
                .matchStatus(match.getStatus())
                .categories(categoryViews)
                .build();
    }
}
