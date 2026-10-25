package com.ipl.event.entity;

import com.ipl.event.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "matches",
        indexes = {
                @Index(name = "idx_match_status", columnList = "status"),
                @Index(name = "idx_match_home_franchise", columnList = "home_franchise_id"),
                @Index(name = "idx_match_ticket_open_at", columnList = "ticket_open_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_franchise_id", nullable = false)
    private Franchise homeFranchise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_franchise_id", nullable = false)
    private Franchise awayFranchise;

    /**
     * Set by the home franchise during match configuration - null until then.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id")
    private Stadium stadium;

    @Column(name = "match_date_time", nullable = false)
    private LocalDateTime matchDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MatchStatus status = MatchStatus.CONFIGURATION_PENDING;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * matchDateTime minus 5 days, set at approval time. A scheduled job
     * (TicketOpeningScheduler) flips status to TICKETS_OPEN once this
     * passes - see that class for why this isn't done synchronously.
     */
    @Column(name = "ticket_open_at")
    private LocalDateTime ticketOpenAt;

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
