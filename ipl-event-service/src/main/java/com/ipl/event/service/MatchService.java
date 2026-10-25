package com.ipl.event.service;

import com.ipl.event.dto.match.CreateMatchRequest;
import com.ipl.event.dto.match.MatchResponse;
import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Match;
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

    private static final long TICKET_OPEN_DAYS_BEFORE_MATCH = 5;

    private final MatchRepository matchRepository;
    private final FranchiseRepository franchiseRepository;
    private final MatchTicketInventoryRepository matchTicketInventoryRepository;
    private final OutboxPublisherService outboxPublisherService;

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest request) {
        if (request.getHomeFranchiseId().equals(request.getAwayFranchiseId())) {
            throw new IllegalArgumentException("Home and away franchise must be different");
        }

        Franchise home = franchiseRepository.findById(request.getHomeFranchiseId())
                .orElseThrow(() -> new FranchiseNotFoundException("Home franchise not found: " + request.getHomeFranchiseId()));
        Franchise away = franchiseRepository.findById(request.getAwayFranchiseId())
                .orElseThrow(() -> new FranchiseNotFoundException("Away franchise not found: " + request.getAwayFranchiseId()));

        Match match = Match.builder()
                .homeFranchise(home)
                .awayFranchise(away)
                .matchDateTime(request.getMatchDateTime())
                .status(MatchStatus.CONFIGURATION_PENDING)
                .build();

        Match saved = matchRepository.save(match);

        MatchScheduledEvent event = new MatchScheduledEvent(
                saved.getId(), home.getId(), home.getFranchiseName(),
                away.getId(), away.getFranchiseName(), saved.getMatchDateTime()
        );

        outboxPublisherService.saveOutboxEvent("MATCH", saved.getId(),
                "MatchScheduledEvent", KafkaTopicConfig.MATCH_SCHEDULED_TOPIC, event);

        return toResponse(saved);
    }

    @Transactional
    public MatchResponse approveMatch(Long matchId) {
        Match match = getMatchOrThrow(matchId);

        if (match.getStatus() != MatchStatus.WAITING_FOR_APPROVAL) {
            throw new InvalidMatchStateException(
                    "Match must be WAITING_FOR_APPROVAL to approve - current status: " + match.getStatus());
        }
        if (match.getStadium() == null || !matchTicketInventoryRepository.existsByMatchId(matchId)) {
            throw new InvalidMatchStateException("Match configuration (stadium + ticket categories) is incomplete");
        }

        LocalDateTime now = LocalDateTime.now();
        match.setApprovedAt(now);
        match.setTicketOpenAt(match.getMatchDateTime().minusDays(TICKET_OPEN_DAYS_BEFORE_MATCH));
        match.setStatus(MatchStatus.APPROVED);
        Match saved = matchRepository.save(match);

        MatchApprovedEvent event = new MatchApprovedEvent(
                saved.getId(), saved.getHomeFranchise().getId(), saved.getAwayFranchise().getId(),
                saved.getHomeFranchise().getFranchiseName(), saved.getAwayFranchise().getFranchiseName(),
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
        List<Match> matches = statusFilter != null
                ? matchRepository.findByStatus(statusFilter)
                : matchRepository.findAll();
        return matches.stream().map(this::toResponse).toList();
    }

    Match getMatchOrThrow(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException("Match not found: " + matchId));
    }

    private MatchResponse toResponse(Match match) {
        return MatchResponse.builder()
                .matchId(match.getId())
                .homeFranchiseId(match.getHomeFranchise().getId())
                .homeTeamName(match.getHomeFranchise().getFranchiseName())
                .homeTeamCode(match.getHomeFranchise().getTeamCode())
                .awayFranchiseId(match.getAwayFranchise().getId())
                .awayTeamName(match.getAwayFranchise().getFranchiseName())
                .awayTeamCode(match.getAwayFranchise().getTeamCode())
                .stadiumId(match.getStadium() != null ? match.getStadium().getId() : null)
                .stadiumName(match.getStadium() != null ? match.getStadium().getName() : null)
                .matchDateTime(match.getMatchDateTime())
                .status(match.getStatus())
                .approvedAt(match.getApprovedAt())
                .ticketOpenAt(match.getTicketOpenAt())
                .build();
    }
}
