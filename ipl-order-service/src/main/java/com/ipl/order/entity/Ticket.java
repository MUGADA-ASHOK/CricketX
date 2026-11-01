package com.ipl.order.entity;

import com.ipl.order.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tickets",
        indexes = {
                @Index(name = "idx_ticket_order_id", columnList = "order_id"),
                @Index(name = "idx_ticket_user_id", columnList = "user_id"),
                @Index(name = "idx_ticket_number", columnList = "ticket_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 64)
    private String ticketNumber;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_number", nullable = false, length = 64)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false, length = 150)
    private String userEmail;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "stand", length = 100)
    private String stand;

    @Column(name = "section", length = 50)
    private String section;

    @Column(name = "seat_number", length = 50)
    private String seatNumber;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.ACTIVE;

    @Column(name = "qr_code_data", nullable = false, length = 500)
    private String qrCodeData;

    @Column(name = "qr_code_base64", columnDefinition = "TEXT")
    private String qrCodeBase64;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Column(name = "turnstile_gate", length = 50)
    private String turnstileGate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

