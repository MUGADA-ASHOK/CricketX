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

    List<Match> findByStatus(MatchStatus status);

    List<Match> findByHomeFranchiseId(Long homeFranchiseId);

    List<Match> findByStatusAndTicketOpenAtLessThanEqual(MatchStatus status, LocalDateTime now);

    Optional<Match> findById(Long id);

    @Modifying
    @Query("UPDATE Match m SET m.status = :newStatus, m.updatedAt = :now WHERE m.status = :oldStatus AND m.ticketOpenAt <= :now")
    int promoteApprovedMatches(
            @Param("oldStatus") MatchStatus oldStatus,
            @Param("newStatus") MatchStatus newStatus,
            @Param("now") LocalDateTime now
    );
}
