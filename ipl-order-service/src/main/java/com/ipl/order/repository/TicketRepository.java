package com.ipl.order.repository;

import com.ipl.order.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByOrderId(Long orderId);

    List<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Optional<Ticket> findByQrCodeData(String qrCodeData);
}

