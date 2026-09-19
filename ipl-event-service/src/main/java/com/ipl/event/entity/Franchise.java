package com.ipl.event.entity;

import com.ipl.event.enums.FranchiseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "franchises",
        indexes = {
                @Index(name = "idx_franchise_user_id", columnList = "user_id"),
                @Index(name = "idx_franchise_team_code", columnList = "team_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Franchise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the user from Auth Service.
     *
     * This is NOT a database foreign key because Auth Service
     * owns the users table.
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "franchise_name", nullable = false, unique = true, length = 100)
    private String franchiseName;

    @Column(name = "team_code", nullable = false, unique = true, length = 10)
    private String teamCode;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(length = 100)
    private String city;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_stadium_id")
    private Stadium homeStadium;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FranchiseStatus status = FranchiseStatus.ACTIVE;

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