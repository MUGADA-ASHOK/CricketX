package com.ipl.order.scheduler;

import com.ipl.order.entity.Order;
import com.ipl.order.entity.OrderItem;
import com.ipl.order.enums.OrderStatus;
import com.ipl.order.repository.OrderRepository;
import com.ipl.order.repository.TicketInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically scans for expired PENDING orders (10-minute timeout) and
 * automatically releases held tickets back to available inventory using
 * pessimistic row locks.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryScheduler {

    private final OrderRepository orderRepository;
    private final TicketInventoryRepository ticketInventoryRepository;

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void cleanupExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndExpiresAtLessThanEqual(OrderStatus.PENDING, now);

        if (expiredOrders.isEmpty()) {
            return;
        }

        for (Order order : expiredOrders) {
            order.setStatus(OrderStatus.EXPIRED);

            for (OrderItem item : order.getItems()) {
                ticketInventoryRepository.findForReservationWithLock(order.getMatchId(), item.getTicketCategoryId())
                        .ifPresent(inventory -> {
                            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + item.getQuantity());
                            ticketInventoryRepository.save(inventory);
                        });
            }
            log.info("Expired reservation order {} and restored held tickets to inventory", order.getOrderNumber());
        }

        orderRepository.saveAll(expiredOrders);
    }
}
