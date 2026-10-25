package com.ipl.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Local inventory replica in Order Service for ultra-fast, local reservation checks
 * without synchronous coupling to Event Service.
 *
 * Concurrency Control: Uses Pessimistic Write Locking (SELECT ... FOR UPDATE)
 * in TicketInventoryRepository to serialize seat allocation across concurrent
 * transaction threads and prevent race conditions or overselling.
 */
@Entity
@Table(
        name = "ticket_inventory",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_match_category", columnNames = {"match_id", "ticket_category_id"})
        },
        indexes = {
                @Index(name = "idx_order_inv_match_id", columnList = "match_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "ticket_category_id", nullable = false)
    private Long ticketCategoryId;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
