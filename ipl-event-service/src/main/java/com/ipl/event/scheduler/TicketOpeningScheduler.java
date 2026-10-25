package com.ipl.event.scheduler;

import com.ipl.event.entity.Match;
import com.ipl.event.enums.MatchStatus;
import com.ipl.event.event.MatchTicketsOpenedEvent;
import com.ipl.event.kafka.KafkaTopicConfig;
import com.ipl.event.kafka.OutboxPublisherService;
import com.ipl.event.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically checks for APPROVED matches whose ticketOpenAt has arrived
 * and safely transitions their status to TICKETS_OPEN, enqueuing a
 * MatchTicketsOpenedEvent in the transactional outbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketOpeningScheduler {

    private final MatchRepository matchRepository;
    private final OutboxPublisherService outboxPublisherService;

    @Scheduled(fixedRateString = "${ticket-opening.check-interval-ms:60000}")
    @Transactional
    public void openTicketsForDueMatches() {
        LocalDateTime now = LocalDateTime.now();
        List<Match> dueMatches = matchRepository.findByStatusAndTicketOpenAtLessThanEqual(
                MatchStatus.APPROVED, now);

        if (dueMatches.isEmpty()) {
            return;
        }

        int updatedCount = matchRepository.promoteApprovedMatches(
                MatchStatus.APPROVED, MatchStatus.TICKETS_OPEN, now);

        if (updatedCount > 0) {
            for (Match match : dueMatches) {
                MatchTicketsOpenedEvent event = new MatchTicketsOpenedEvent(
                        match.getId(),
                        match.getHomeFranchise().getId(),
                        match.getAwayFranchise().getId(),
                        match.getMatchDateTime(),
                        match.getTicketOpenAt()
                );
                outboxPublisherService.saveOutboxEvent(
                        "MATCH",
                        match.getId(),
                        "MatchTicketsOpenedEvent",
                        KafkaTopicConfig.MATCH_TICKETS_OPENED_TOPIC,
                        event
                );
            }
            log.info("Opened ticket sales for {} match(es): {}", updatedCount,
                    dueMatches.stream().map(Match::getId).toList());
        }
    }
}
