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

    private JsonNode parsePayload(String message) throws Exception {
        JsonNode node = objectMapper.readTree(message);
        if (node.isTextual()) {
            return objectMapper.readTree(node.asText());
        }
        return node;
    }

    @KafkaListener(topics = "ipl.order.confirmed", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleOrderConfirmed(String message) {
        try {
            JsonNode node = parsePayload(message);
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
            JsonNode node = parsePayload(message);
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

    @KafkaListener(topics = "ipl.match.approved", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleMatchApproved(String message) {
        try {
            JsonNode node = parsePayload(message);
            Long matchId = node.has("matchId") ? node.get("matchId").asLong() : 0L;
            String homeName = node.has("homeTeam") ? node.get("homeTeam").asText() : (node.has("homeFranchiseName") ? node.get("homeFranchiseName").asText() : "Home Team");
            String awayName = node.has("awayTeam") ? node.get("awayTeam").asText() : (node.has("awayFranchiseName") ? node.get("awayFranchiseName").asText() : "Away Team");
            String stadium = node.has("stadiumName") ? node.get("stadiumName").asText() : "IPL Arena Ground";
            Long homeUserId = node.has("homeUserId") && !node.get("homeUserId").isNull() ? node.get("homeUserId").asLong() : null;
            Long awayUserId = node.has("awayUserId") && !node.get("awayUserId").isNull() ? node.get("awayUserId").asLong() : null;

            // 1. Notify Home Franchise
            if (homeUserId != null) {
                String title = String.format("Match #%d Sanctioned by BCCI!", matchId);
                String msg = String.format("BCCI has sanctioned Match #%d: %s vs %s at %s! Your ticket pricing is approved. Live ticket sales open 7 days before match day.",
                        matchId, homeName, awayName, stadium);
                notificationService.sendNotification(homeUserId, title, msg, NotificationType.MATCH_APPROVED, "MATCH", String.valueOf(matchId));
            }

            // 2. Notify Away Franchise
            if (awayUserId != null) {
                String title = String.format("Fixture Sanctioned by BCCI: %s vs %s", homeName, awayName);
                String msg = String.format("Match #%d: %s vs %s at %s has been sanctioned by BCCI! Live ticket sales open 7 days before match day.",
                        matchId, homeName, awayName, stadium);
                notificationService.sendNotification(awayUserId, title, msg, NotificationType.MATCH_APPROVED, "MATCH", String.valueOf(matchId));
            }

            // 3. Broadcast to Fans
            String fanTitle = String.format("Tickets Opening Soon: %s vs %s", homeName, awayName);
            String fanMsg = String.format("BCCI has officially sanctioned Match #%d: %s vs %s at %s! Live booking opens 7 days before the clash.",
                    matchId, homeName, awayName, stadium);
            notificationService.broadcastAnnouncement(fanTitle, fanMsg);

            log.info("Dispatched match approved notifications for matchId={}", matchId);
        } catch (Exception ex) {
            log.error("Failed to parse ipl.match.approved event: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ipl.match.configured", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleMatchConfigured(String message) {
        try {
            JsonNode node = parsePayload(message);
            Long matchId = node.has("matchId") ? node.get("matchId").asLong() : 0L;
            String homeName = node.has("homeTeam") ? node.get("homeTeam").asText() : "Home Team";
            String awayName = node.has("awayTeam") ? node.get("awayTeam").asText() : "Away Team";
            String stadium = node.has("stadiumName") ? node.get("stadiumName").asText() : "Stadium Venue";
            int totalSeats = node.has("totalAllocatedSeats") ? node.get("totalAllocatedSeats").asInt() : 0;

            String title = String.format("Action Required: Match #%d Pricing Configured", matchId);
            String msg = String.format("%s has submitted ticket pricing & quotas (%d seats allocated) for Match #%d (%s vs %s) at %s. Please review and sanction for live ticket sales.",
                    homeName, totalSeats, matchId, homeName, awayName, stadium);

            // Notify BCCI Admin role
            notificationService.sendRoleNotification("ADMIN", title, msg, NotificationType.MATCH_CONFIGURED, "MATCH", String.valueOf(matchId));
            log.info("Dispatched MatchConfigured notification to BCCI ADMIN for matchId={}", matchId);
        } catch (Exception ex) {
            log.error("Failed to parse ipl.match.configured event: {}", ex.getMessage());
        }
    }

    @KafkaListener(topics = "ipl.match.tickets-opened", groupId = "${spring.kafka.consumer.group-id:ipl-notification-group}")
    public void handleTicketsOpened(String message) {
        try {
            JsonNode node = parsePayload(message);
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
            JsonNode node = parsePayload(message);
            Long matchId = node.get("matchId").asLong();
            String homeName = node.has("homeTeam") ? node.get("homeTeam").asText() : (node.has("homeFranchiseName") ? node.get("homeFranchiseName").asText() : "Home");
            String awayName = node.has("awayTeam") ? node.get("awayTeam").asText() : (node.has("awayFranchiseName") ? node.get("awayFranchiseName").asText() : "Away");
            String stadium = node.has("stadiumName") ? node.get("stadiumName").asText() : "Assigned Stadium";
            String matchTime = node.has("matchDateTime") ? node.get("matchDateTime").asText() : "TBD";
            Long homeUserId = node.has("homeUserId") && !node.get("homeUserId").isNull() ? node.get("homeUserId").asLong() : null;
            Long awayUserId = node.has("awayUserId") && !node.get("awayUserId").isNull() ? node.get("awayUserId").asLong() : null;

            // 1. Special notification for Home Franchise
            if (homeUserId != null) {
                String hostTitle = "Host Fixture Scheduled - Action Required: Configure Pricing";
                String hostMsg = String.format("You are hosting %s at %s for Match #%d. Please configure ticket tier quotas and pricing for your home venue.",
                        awayName, stadium, matchId);
                notificationService.sendNotification(homeUserId, hostTitle, hostMsg, NotificationType.MATCH_SCHEDULED, "MATCH", String.valueOf(matchId));
            }

            // 2. Notification for Away Franchise
            if (awayUserId != null) {
                String awayTitle = String.format("Away Fixture Scheduled: %s vs %s", homeName, awayName);
                String awayMsg = String.format("Your team %s will play away against %s at %s (Match #%d). Pricing will be configured by %s.",
                        awayName, homeName, stadium, matchId, homeName);
                notificationService.sendNotification(awayUserId, awayTitle, awayMsg, NotificationType.MATCH_SCHEDULED, "MATCH", String.valueOf(matchId));
            }

            // 3. Broadcast announcement to all fans
            String fanTitle = String.format("Fixture Announced: %s vs %s", homeName, awayName);
            String fanMsg = String.format("Match #%d: %s vs %s scheduled at %s. Live ticket booking opens 7 days prior to match day.",
                    matchId, homeName, awayName, stadium);
            notificationService.broadcastAnnouncement(fanTitle, fanMsg);

            log.info("Dispatched match scheduled notifications for matchId={}", matchId);
        } catch (Exception ex) {
            log.error("Failed to parse ipl.match.scheduled event: {}", ex.getMessage());
        }
    }
}
