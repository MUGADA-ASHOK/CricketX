package com.ipl.order.repository;

import com.ipl.order.entity.Order;
import com.ipl.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByStatusAndExpiresAtLessThanEqual(OrderStatus status, LocalDateTime now);
}
