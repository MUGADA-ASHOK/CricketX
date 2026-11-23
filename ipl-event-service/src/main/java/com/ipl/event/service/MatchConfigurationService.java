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
import com.ipl.event.event.MatchConfiguredEvent;
import com.ipl.event.kafka.KafkaTopicConfig;
import com.ipl.event.kafka.OutboxPublisherService;
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
    private final OutboxPublisherService outboxPublisherService;

    @Transactional
    public MatchConfigurationResponse createConfiguration(Long matchId, AuthenticatedUser user, MatchConfigurationRequest request) {
        Match match = getOwnedMatchOrThrow(matchId, user);

        if (match.getStatus() == MatchStatus.APPROVED || match.getStatus() == MatchStatus.TICKETS_OPEN) {
            throw new InvalidMatchStateException(
                    "Match is already approved for sale and cannot be reconfigured - current status: " + match.getStatus());
        }

        // Idempotently clear previous draft allocations if re-configuring before BCCI approval
        if (matchTicketInventoryRepository.existsByMatchId(matchId)) {
            matchTicketInventoryRepository.deleteAll(matchTicketInventoryRepository.findByMatchId(matchId));
        }

        applyConfiguration(match, request);
        match.setStatus(MatchStatus.WAITING_FOR_APPROVAL);
        Match saved = matchRepository.save(match);

        int totalAllocated = request.getCategories().stream()
                .mapToInt(MatchConfigurationRequest.TicketCategoryConfig::getCapacity)
                .sum();

        MatchConfiguredEvent event = new MatchConfiguredEvent(
                saved.getId(),
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getId() : null,
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getUserId() : null,
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getFranchiseName() : "Home Team",
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getId() : null,
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getUserId() : null,
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getFranchiseName() : "Away Team",
                saved.getStadium() != null ? saved.getStadium().getName() : "Stadium Venue",
                saved.getMatchDateTime(),
                totalAllocated
        );

        outboxPublisherService.saveOutboxEvent(
                "MATCH", saved.getId(),
                "MatchConfiguredEvent", KafkaTopicConfig.MATCH_CONFIGURED_TOPIC, event
        );

        return toResponse(saved);
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
        Match saved = matchRepository.save(match);

        int totalAllocated = request.getCategories().stream()
                .mapToInt(MatchConfigurationRequest.TicketCategoryConfig::getCapacity)
                .sum();

        MatchConfiguredEvent event = new MatchConfiguredEvent(
                saved.getId(),
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getId() : null,
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getUserId() : null,
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getFranchiseName() : "Home Team",
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getId() : null,
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getUserId() : null,
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getFranchiseName() : "Away Team",
                saved.getStadium() != null ? saved.getStadium().getName() : "Stadium Venue",
                saved.getMatchDateTime(),
                totalAllocated
        );

        outboxPublisherService.saveOutboxEvent(
                "MATCH", saved.getId(),
                "MatchConfiguredEvent", KafkaTopicConfig.MATCH_CONFIGURED_TOPIC, event
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MatchConfigurationResponse getConfiguration(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new com.ipl.event.exception.MatchNotFoundException("Match not found: " + matchId));
        return toResponse(match);
    }

    private void applyConfiguration(Match match, MatchConfigurationRequest request) {
        // Enforce: The selected home stadium assigned by BCCI cannot change!
        Stadium stadium = match.getStadium();
        if (stadium == null) {
            if (request.getStadiumId() != null) {
                stadium = stadiumRepository.findById(request.getStadiumId()).orElse(null);
            }
            if (stadium == null && match.getHomeFranchise() != null && match.getHomeFranchise().getHomeStadium() != null) {
                stadium = match.getHomeFranchise().getHomeStadium();
            }
            if (stadium == null) {
                stadium = stadiumRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new StadiumNotFoundException("No stadium assigned to this match"));
            }
            match.setStadium(stadium);
        }

        final Stadium finalStadium = stadium;

        int totalRequestedCapacity = request.getCategories().stream()
                .mapToInt(MatchConfigurationRequest.TicketCategoryConfig::getCapacity)
                .sum();
        if (totalRequestedCapacity > finalStadium.getCapacity()) {
            throw new InvalidTicketConfigurationException(
                    "Total ticket category capacity (" + totalRequestedCapacity
                            + ") exceeds stadium capacity (" + finalStadium.getCapacity() + ")");
        }

        for (MatchConfigurationRequest.TicketCategoryConfig categoryConfig : request.getCategories()) {
            TicketCategory ticketCategory = ticketCategoryRepository
                    .findByStadiumId(finalStadium.getId()).stream()
                    .filter(tc -> tc.getCategoryName().equalsIgnoreCase(categoryConfig.getCategoryName()))
                    .findFirst()
                    .orElseGet(() -> ticketCategoryRepository.save(TicketCategory.builder()
                            .stadium(finalStadium)
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
    }

    /**
     * Resolves the match AND verifies the authenticated user's own
     * franchise is the designated home franchise.
     * Only the home franchise (or BCCI admin) is authorized to configure ticket pricing.
     */
    private Match getOwnedMatchOrThrow(Long matchId, AuthenticatedUser user) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new com.ipl.event.exception.MatchNotFoundException("Match not found: " + matchId));

        String role = user.role();
        if (role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("ROLE_ADMIN"))) {
            return match;
        }

        Franchise franchise = franchiseRepository.findByUserId(user.userId())
                .orElseThrow(() -> new UnauthorizedFranchiseException("Only registered franchises can configure match pricing"));

        if (!franchise.getId().equals(match.getHomeFranchise().getId())) {
            throw new UnauthorizedFranchiseException(
                    "Pricing configuration can only be done by the home franchise (" 
                    + match.getHomeFranchise().getFranchiseName() + ")");
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
                .id(match.getId())
                .stadiumId(match.getStadium() != null ? match.getStadium().getId() : null)
                .stadiumName(match.getStadium() != null ? match.getStadium().getName() : null)
                .matchStatus(match.getStatus())
                .categories(categoryViews)
                .build();
    }
}
