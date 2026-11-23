package com.ipl.event.repository;

import com.ipl.event.entity.Match;
import com.ipl.event.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m LEFT JOIN FETCH m.homeFranchise LEFT JOIN FETCH m.awayFranchise LEFT JOIN FETCH m.stadium WHERE m.status = :status ORDER BY m.matchDateTime ASC")
    List<Match> findByStatusWithDetails(@Param("status") MatchStatus status);

    @Query("SELECT m FROM Match m LEFT JOIN FETCH m.homeFranchise LEFT JOIN FETCH m.awayFranchise LEFT JOIN FETCH m.stadium ORDER BY m.matchDateTime ASC")
    List<Match> findAllWithDetails();

    List<Match> findByStatus(MatchStatus status);

    List<Match> findByHomeFranchiseId(Long homeFranchiseId);

    @Query("SELECT m FROM Match m LEFT JOIN FETCH m.homeFranchise LEFT JOIN FETCH m.awayFranchise WHERE m.status = :status AND m.ticketOpenAt <= :now")
    List<Match> findByStatusAndTicketOpenAtLessThanEqual(@Param("status") MatchStatus status, @Param("now") LocalDateTime now);

    Optional<Match> findById(Long id);

    @Modifying
    @Query("UPDATE Match m SET m.status = :newStatus, m.updatedAt = :now WHERE m.status = :oldStatus AND m.ticketOpenAt <= :now")
    int promoteApprovedMatches(
            @Param("oldStatus") MatchStatus oldStatus,
            @Param("newStatus") MatchStatus newStatus,
            @Param("now") LocalDateTime now
    );
}
