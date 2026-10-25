package com.ipl.order.repository;

import com.ipl.order.entity.TicketInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketInventoryRepository extends JpaRepository<TicketInventory, Long> {

    List<TicketInventory> findByMatchId(Long matchId);

    Optional<TicketInventory> findByMatchIdAndTicketCategoryId(Long matchId, Long ticketCategoryId);

    /**
     * Acquires an exclusive database-level row lock (SELECT ... FOR UPDATE)
     * to serialize ticket reservation, preventing any concurrent overselling or race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TicketInventory t WHERE t.matchId = :matchId AND t.ticketCategoryId = :categoryId")
    Optional<TicketInventory> findForReservationWithLock(@Param("matchId") Long matchId, @Param("categoryId") Long categoryId);
}
