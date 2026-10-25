package com.ipl.order.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipl.order.entity.TicketInventory;
import com.ipl.order.repository.TicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final TicketInventoryRepository ticketInventoryRepository;

    @KafkaListener(topics = "ipl.match.approved", groupId = "${spring.kafka.consumer.group-id:ipl-order-group}")
    @Transactional
    public void handleMatchApproved(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long matchId = node.get("matchId").asLong();
            log.info("Received MatchApprovedEvent from Kafka for matchId={}", matchId);

            // Initialize standard baseline inventory if not already present
            if (ticketInventoryRepository.findByMatchId(matchId).isEmpty()) {
                initDefaultInventory(matchId);
            }
        } catch (Exception ex) {
            log.error("Failed to process MatchApprovedEvent from Kafka: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ipl.match.tickets-opened", groupId = "${spring.kafka.consumer.group-id:ipl-order-group}")
    @Transactional
    public void handleMatchTicketsOpened(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long matchId = node.get("matchId").asLong();
            log.info("Received MatchTicketsOpenedEvent from Kafka for matchId={}. Ticket sales live!", matchId);
        } catch (Exception ex) {
            log.error("Failed to process MatchTicketsOpenedEvent: {}", ex.getMessage());
        }
    }

    private void initDefaultInventory(Long matchId) {
        TicketInventory vip = TicketInventory.builder()
                .matchId(matchId)
                .ticketCategoryId(1L)
                .categoryName("VIP Lounge")
                .price(BigDecimal.valueOf(15000.00))
                .totalQuantity(500)
                .availableQuantity(500)
                .build();

        TicketInventory premium = TicketInventory.builder()
                .matchId(matchId)
                .ticketCategoryId(2L)
                .categoryName("Premium Pavilion")
                .price(BigDecimal.valueOf(5000.00))
                .totalQuantity(2500)
                .availableQuantity(2500)
                .build();

        TicketInventory general = TicketInventory.builder()
                .matchId(matchId)
                .ticketCategoryId(3L)
                .categoryName("General Stand")
                .price(BigDecimal.valueOf(1500.00))
                .totalQuantity(25000)
                .availableQuantity(25000)
                .build();

        ticketInventoryRepository.save(vip);
        ticketInventoryRepository.save(premium);
        ticketInventoryRepository.save(general);
        log.info("Initialized local ticket inventory replica for matchId={}", matchId);
    }
}
