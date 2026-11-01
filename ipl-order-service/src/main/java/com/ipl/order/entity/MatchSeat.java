package com.ipl.order.entity;

import com.ipl.order.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "match_seats",
        indexes = {
                @Index(name = "idx_seat_match_id", columnList = "match_id"),
                @Index(name = "idx_seat_status", columnList = "status"),
                @Index(name = "idx_seat_order_id", columnList = "order_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_seat", columnNames = {"match_id", "seat_number"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "ticket_category_id", nullable = false)
    private Long ticketCategoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "stand", nullable = false, length = 100)
    private String stand;

    @Column(name = "section", nullable = false, length = 50)
    private String section;

    @Column(name = "row_name", nullable = false, length = 20)
    private String rowName;

    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    @Column(name = "order_id")
    private Long orderId;

    @Version
    private Long version;
}

