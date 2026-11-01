package com.ipl.order.repository;

import com.ipl.order.entity.MatchSeat;
import com.ipl.order.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MatchSeatRepository extends JpaRepository<MatchSeat, Long> {

    List<MatchSeat> findByMatchIdOrderByStandAscRowNameAscSeatNumberAsc(Long matchId);

    List<MatchSeat> findByMatchIdAndStatus(Long matchId, SeatStatus status);

    List<MatchSeat> findByMatchIdAndTicketCategoryId(Long matchId, Long ticketCategoryId);

    List<MatchSeat> findByIdIn(List<Long> ids);

    List<MatchSeat> findByOrderId(Long orderId);

    @Modifying
    @Query("UPDATE MatchSeat s SET s.status = :toStatus, s.heldByUserId = null, s.heldUntil = null, s.orderId = null " +
           "WHERE s.status = :fromStatus AND s.heldUntil <= :now")
    int releaseExpiredSeats(@Param("fromStatus") SeatStatus fromStatus,
                            @Param("toStatus") SeatStatus toStatus,
                            @Param("now") LocalDateTime now);
}

