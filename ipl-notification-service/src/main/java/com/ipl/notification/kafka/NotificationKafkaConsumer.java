package com.ipl.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipl.notification.enums.NotificationType;
import com.ipl.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = "ipl.order.confirmed", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleOrderConfirmed(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long userId = node.get("userId").asLong();
            String orderNumber = node.get("orderNumber").asText();
            Long matchId = node.get("matchId").asLong();
            double totalAmount = node.has("totalAmount") ? node.get("totalAmount").asDouble() : 0.0;

            String title = "Booking Confirmed! Tickets Issued";
            String msg = String.format("Success! Your booking %s for Match #%d (₹%.2f) is confirmed. Your digital tickets with QR codes are ready in My Tickets.",
                    orderNumber, matchId, totalAmount);

            notificationService.sendNotification(userId, title, msg, NotificationType.ORDER_CONFIRMED, "ORDER", orderNumber);
            log.info("Dispatched in-system notification for confirmed order {} to user {}", orderNumber, userId);
        } catch (Exception ex) {
            log.error("Failed to parse ipl.order.confirmed event: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ipl.ticket.released", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleTicketReleased(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String orderNumber = node.get("orderNumber").asText();
            Long userId = node.has("userId") ? node.get("userId").asLong() : null;

            if (userId != null) {
                String title = "Reservation Cancelled";
                String msg = String.format("Your ticket reservation %s was cancelled and seats were returned to inventory.", orderNumber);
                notificationService.sendNotification(userId, title, msg, NotificationType.TICKET_RELEASED, "ORDER", orderNumber);
            }
        } catch (Exception ex) {
            log.error("Failed to parse ipl.ticket.released event: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ipl.match.tickets-opened", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleTicketsOpened(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long matchId = node.get("matchId").asLong();

            String title = "Ticket Sales Are Now Live!";
            String msg = String.format("Ticket bookings for Match #%d are now officially OPEN! Reserve your favorite seats now before they sell out.", matchId);

            notificationService.broadcastAnnouncement(title, msg);
            log.info("Broadcasted tickets-opened notification for matchId={}", matchId);
        } catch (Exception ex) {
            log.error("Failed to parse ipl.match.tickets-opened event: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ipl.match.scheduled", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleMatchScheduled(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long matchId = node.get("matchId").asLong();
            String homeName = node.has("homeFranchiseName") ? node.get("homeFranchiseName").asText() : "Home";
            String awayName = node.has("awayFranchiseName") ? node.get("awayFranchiseName").asText() : "Away";

            String title = "New Match Scheduled!";
            String msg = String.format("Fixture Announced: %s vs %s (Match #%d). Ticket sales will open 5 days before match day.", homeName, awayName, matchId);

            notificationService.broadcastAnnouncement(title, msg);
        } catch (Exception ex) {
            log.error("Failed to parse ipl.match.scheduled event: {}", ex.getMessage());
        }
    }
}

