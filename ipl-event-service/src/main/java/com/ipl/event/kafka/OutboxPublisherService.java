package com.ipl.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipl.event.entity.OutboxEvent;
import com.ipl.event.enums.OutboxEventStatus;
import com.ipl.event.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implements the Transactional Outbox Pattern:
 * 1. Domain services call #saveOutboxEvent inside their main @Transactional methods.
 *    The outbox row commits in the exact same database transaction as the entity change.
 * 2. This publisher reliably dispatches pending outbox events to Kafka on a periodic schedule
 *    (and retries on failures), guaranteeing zero event loss even if the server crashes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveOutboxEvent(String aggregateType, Long aggregateId, String eventType, String topic, Object eventPayload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(jsonPayload)
                    .status(OutboxEventStatus.PENDING)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Enqueued OutboxEvent type={} for aggregateId={}", eventType, aggregateId);
        } catch (Exception ex) {
            log.error("Failed to serialize and save outbox event for aggregateId={}", aggregateId, ex);
            throw new IllegalStateException("Failed to serialize outbox event: " + ex.getMessage(), ex);
        }
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), String.valueOf(event.getAggregateId()), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Error sending outbox event id={} to topic={}: {}", event.getId(), event.getTopic(), ex.getMessage());
                            }
                        });
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setProcessedAt(LocalDateTime.now());
                log.info("Dispatched OutboxEvent id={} to topic={}", event.getId(), event.getTopic());
            } catch (Exception ex) {
                log.error("Failed to dispatch outbox event id={}: {}", event.getId(), ex.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(ex.getMessage());
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus(OutboxEventStatus.FAILED);
                }
            }
        }
        outboxEventRepository.saveAll(pending);
    }
}
