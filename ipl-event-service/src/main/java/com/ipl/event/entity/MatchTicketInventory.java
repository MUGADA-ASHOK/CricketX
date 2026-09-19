package com.ipl.event.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "match_ticket_inventory",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_match_ticket_category",
                        columnNames = {"match_id", "ticket_category_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_inventory_match_id",
                        columnList = "match_id"
                ),
                @Index(
                        name = "idx_inventory_ticket_category_id",
                        columnList = "ticket_category_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchTicketInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the Match from the Match entity.
     *
     * This represents the match for which this inventory exists.
     */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /**
     * Ticket category configured for the stadium.
     *
     * Example:
     * VIP, Premium, General
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_category_id", nullable = false)
    private TicketCategory ticketCategory;

    /**
     * Total number of tickets made available
     * for this category for this particular match.
     *
     * Usually this will initially equal TicketCategory.capacity
     * but keeping it here allows the franchise/admin to allocate
     * a different quantity for a particular match if required.
     */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    /**
     * Number of tickets currently available for booking.
     *
     * This value will decrease when users successfully book tickets.
     *
     * This field is important for concurrency control.
     */
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    /**
     * Price of this ticket category for this particular match.
     *
     * Price can differ between matches.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
