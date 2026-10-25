package com.ipl.event.repository;

import com.ipl.event.entity.MatchTicketInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchTicketInventoryRepository extends JpaRepository<MatchTicketInventory, Long> {

    List<MatchTicketInventory> findByMatchId(Long matchId);

    boolean existsByMatchId(Long matchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MatchTicketInventory m WHERE m.matchId = :matchId AND m.ticketCategory.id = :categoryId")
    Optional<MatchTicketInventory> findByMatchIdAndTicketCategoryIdWithLock(
            @Param("matchId") Long matchId, @Param("categoryId") Long categoryId);
}
