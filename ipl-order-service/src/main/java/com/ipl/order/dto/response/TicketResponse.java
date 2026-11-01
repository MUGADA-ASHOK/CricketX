package com.ipl.order.dto.response;

import com.ipl.order.entity.Ticket;
import com.ipl.order.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private Long id;
    private String ticketNumber;
    private String orderNumber;
    private Long matchId;
    private String categoryName;
    private String stand;
    private String section;
    private String seatNumber;
    private BigDecimal price;
    private TicketStatus status;
    private String qrCodeData;
    private String qrCodeBase64;
    private LocalDateTime scannedAt;
    private String turnstileGate;
    private LocalDateTime createdAt;

    public static TicketResponse fromEntity(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .orderNumber(ticket.getOrderNumber())
                .matchId(ticket.getMatchId())
                .categoryName(ticket.getCategoryName())
                .stand(ticket.getStand())
                .section(ticket.getSection())
                .seatNumber(ticket.getSeatNumber())
                .price(ticket.getPrice())
                .status(ticket.getStatus())
                .qrCodeData(ticket.getQrCodeData())
                .qrCodeBase64(ticket.getQrCodeBase64())
                .scannedAt(ticket.getScannedAt())
                .turnstileGate(ticket.getTurnstileGate())
                .createdAt(ticket.getCreatedAt())
                .build();
    }
}

