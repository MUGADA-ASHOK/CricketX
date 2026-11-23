package com.ipl.event.service;

import com.ipl.event.dto.match.CreateMatchRequest;
import com.ipl.event.dto.match.MatchResponse;
import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Match;
import com.ipl.event.entity.Stadium;
import com.ipl.event.enums.MatchStatus;
import com.ipl.event.event.MatchApprovedEvent;
import com.ipl.event.event.MatchScheduledEvent;
import com.ipl.event.exception.FranchiseNotFoundException;
import com.ipl.event.exception.InvalidMatchStateException;
import com.ipl.event.exception.MatchNotFoundException;
import com.ipl.event.kafka.KafkaTopicConfig;
import com.ipl.event.kafka.OutboxPublisherService;
import com.ipl.event.repository.FranchiseRepository;
import com.ipl.event.repository.MatchRepository;
import com.ipl.event.repository.MatchTicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BCCI/ADMIN-side match lifecycle: create, list, get, approve.
 * Employs the Transactional Outbox Pattern to guarantee at-least-once
 * Kafka delivery.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final long TICKET_OPEN_DAYS_BEFORE_MATCH = 7;

    private final MatchRepository matchRepository;
    private final FranchiseRepository franchiseRepository;
    private final MatchTicketInventoryRepository matchTicketInventoryRepository;
    private final OutboxPublisherService outboxPublisherService;
    private final com.ipl.event.repository.StadiumRepository stadiumRepository;
    private final com.ipl.event.repository.TicketCategoryRepository ticketCategoryRepository;

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest request) {
        if (request.getHomeFranchiseId().equals(request.getAwayFranchiseId())) {
            throw new IllegalArgumentException("Home and away franchise must be different");
        }

        Franchise home = franchiseRepository.findById(request.getHomeFranchiseId())
                .orElseThrow(() -> new FranchiseNotFoundException("Home franchise not found: " + request.getHomeFranchiseId()));
        Franchise away = franchiseRepository.findById(request.getAwayFranchiseId())
                .orElseThrow(() -> new FranchiseNotFoundException("Away franchise not found: " + request.getAwayFranchiseId()));

        Stadium stadium = null;
        if (request.getStadiumId() != null) {
            stadium = stadiumRepository.findById(request.getStadiumId()).orElse(null);
        }
        if (stadium == null && home.getHomeStadium() != null) {
            stadium = home.getHomeStadium();
        }
        if (stadium == null) {
            stadium = stadiumRepository.findAll().stream().findFirst().orElse(null);
        }

        Match match = Match.builder()
                .homeFranchise(home)
                .awayFranchise(away)
                .stadium(stadium)
                .matchDateTime(request.getMatchDateTime())
                .status(MatchStatus.CONFIGURATION_PENDING)
                .build();

        Match saved = matchRepository.save(match);

        MatchScheduledEvent event = new MatchScheduledEvent(
                saved.getId(), home.getId(), home.getUserId(), home.getFranchiseName(),
                away.getId(), away.getUserId(), away.getFranchiseName(),
                saved.getStadium() != null ? saved.getStadium().getName() : "IPL Arena Ground",
                saved.getMatchDateTime()
        );

        outboxPublisherService.saveOutboxEvent("MATCH", saved.getId(),
                "MatchScheduledEvent", KafkaTopicConfig.MATCH_SCHEDULED_TOPIC, event);

        return toResponse(saved);
    }

    @Transactional
    public MatchResponse approveMatch(Long matchId) {
        Match match = getMatchOrThrow(matchId);

        // Idempotency check: If match is already approved, return existing match safely
        if (match.getStatus() == MatchStatus.APPROVED || match.getStatus() == MatchStatus.TICKETS_OPEN) {
            return toResponse(match);
        }

        if (match.getStatus() != MatchStatus.WAITING_FOR_APPROVAL) {
            throw new InvalidMatchStateException(
                    "Match must be WAITING_FOR_APPROVAL to approve - current status: " + match.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        match.setApprovedAt(now);
        LocalDateTime ticketOpen = match.getMatchDateTime().minusDays(TICKET_OPEN_DAYS_BEFORE_MATCH);
        match.setTicketOpenAt(ticketOpen);
        if (ticketOpen.isBefore(now) || ticketOpen.isEqual(now)) {
            match.setStatus(MatchStatus.TICKETS_OPEN);
        } else {
            match.setStatus(MatchStatus.APPROVED);
        }
        Match saved = matchRepository.save(match);

        MatchApprovedEvent event = new MatchApprovedEvent(
                saved.getId(),
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getId() : null,
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getUserId() : null,
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getId() : null,
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getUserId() : null,
                saved.getHomeFranchise() != null ? saved.getHomeFranchise().getFranchiseName() : "Home Team",
                saved.getAwayFranchise() != null ? saved.getAwayFranchise().getFranchiseName() : "Away Team",
                saved.getStadium() != null ? saved.getStadium().getName() : "IPL Arena Ground",
                saved.getMatchDateTime(), saved.getTicketOpenAt()
        );

        outboxPublisherService.saveOutboxEvent("MATCH", saved.getId(),
                "MatchApprovedEvent", KafkaTopicConfig.MATCH_APPROVED_TOPIC, event);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long matchId) {
        return toResponse(getMatchOrThrow(matchId));
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listMatches(MatchStatus statusFilter) {
        List<Match> matches;
        try {
            matches = statusFilter != null
                    ? matchRepository.findByStatusWithDetails(statusFilter)
                    : matchRepository.findAllWithDetails();
        } catch (Exception ex) {
            matches = statusFilter != null
                    ? matchRepository.findByStatus(statusFilter)
                    : matchRepository.findAll();
        }
        return matches.stream().map(this::toResponse).toList();
    }

    Match getMatchOrThrow(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException("Match not found: " + matchId));
    }

    private MatchResponse toResponse(Match match) {
        Franchise home = match.getHomeFranchise();
        Franchise away = match.getAwayFranchise();
        Stadium stadium = match.getStadium();

        return MatchResponse.builder()
                .matchId(match.getId())
                .id(match.getId())
                .homeFranchiseId(home != null ? home.getId() : null)
                .homeTeamName(home != null && home.getFranchiseName() != null ? home.getFranchiseName() : (home != null && home.getTeamCode() != null ? home.getTeamCode() : "Home Team"))
                .homeTeamCode(home != null && home.getTeamCode() != null ? home.getTeamCode() : "TBD")
                .awayFranchiseId(away != null ? away.getId() : null)
                .awayTeamName(away != null && away.getFranchiseName() != null ? away.getFranchiseName() : (away != null && away.getTeamCode() != null ? away.getTeamCode() : "Away Team"))
                .awayTeamCode(away != null && away.getTeamCode() != null ? away.getTeamCode() : "TBD")
                .stadiumId(stadium != null ? stadium.getId() : null)
                .stadiumName(stadium != null && stadium.getName() != null ? stadium.getName() : "IPL Arena Ground")
                .matchDateTime(match.getMatchDateTime())
                .status(match.getStatus())
                .approvedAt(match.getApprovedAt())
                .ticketOpenAt(match.getTicketOpenAt())
                .build();
    }
}
