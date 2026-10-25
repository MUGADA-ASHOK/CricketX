package com.ipl.event.kafka;

import com.ipl.event.event.MatchApprovedEvent;
import com.ipl.event.event.MatchScheduledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for MatchScheduledEvent/MatchApprovedEvent as Spring application
 * events and only publishes to Kafka AFTER_COMMIT - i.e. MatchService just
 * calls ApplicationEventPublisher.publishEvent(event) inside its normal
 * @Transactional method, and Spring guarantees this listener only fires if
 * that transaction actually commits. This is the simplest way to satisfy
 * "never publish for a transaction that rolls back" without the service
 * layer having to manually sequence "save, then publish" itself.
 *
 * Note the tradeoff versus a true Outbox Pattern: if the process crashes
 * between DB commit and the Kafka send actually going out, the event is
 * lost (no at-least-once guarantee across a crash). That's an acceptable
 * gap for this stage - the Outbox Pattern would close it but is explicitly
 * out of scope for now (mentioned here as the future production upgrade).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchScheduled(MatchScheduledEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.MATCH_SCHEDULED_TOPIC, event.matchId().toString(), event);
        log.info("Published MatchScheduledEvent for matchId={}", event.matchId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchApproved(MatchApprovedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.MATCH_APPROVED_TOPIC, event.matchId().toString(), event);
        log.info("Published MatchApprovedEvent for matchId={}", event.matchId());
    }
}

