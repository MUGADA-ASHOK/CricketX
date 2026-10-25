package com.ipl.order.service;

import com.ipl.order.dto.request.CreateOrderRequest;
import com.ipl.order.dto.request.PaymentRequest;
import com.ipl.order.dto.response.InventoryResponse;
import com.ipl.order.dto.response.OrderResponse;
import com.ipl.order.entity.Order;
import com.ipl.order.entity.OrderItem;
import com.ipl.order.entity.TicketInventory;
import com.ipl.order.enums.OrderStatus;
import com.ipl.order.exception.InsufficientInventoryException;
import com.ipl.order.exception.InvalidOrderStateException;
import com.ipl.order.exception.OrderExpiredException;
import com.ipl.order.exception.OrderNotFoundException;
import com.ipl.order.repository.OrderRepository;
import com.ipl.order.repository.TicketInventoryRepository;
import com.ipl.order.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ipl.order.dto.response.RazorpayOrderResponse;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.Map;

/**
 * Core Order & Ticket Reservation Service.
 * Implements a Two-Phase Ticket Hold pattern using Pessimistic Locking (SELECT ... FOR UPDATE):
 * 1. Reserve phase: Acquires exclusive row locks on requested ticket categories,
 *    verifies stock, decrements available count, and holds seats in PENDING state for 10 minutes.
 * 2. Pay phase: Confirms the order upon payment receipt.
 * 3. Cancel/Expire phase: Acquires exclusive row locks and restores held seats back to available inventory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int RESERVATION_HOLD_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final TicketInventoryRepository ticketInventoryRepository;
    private final RazorpayService razorpayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderResponse reserveTickets(CreateOrderRequest request, AuthenticatedUser user) {
        LocalDateTime now = LocalDateTime.now();
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase())
                .userId(user.userId())
                .userEmail(user.email())
                .matchId(request.getMatchId())
                .status(OrderStatus.PENDING)
                .expiresAt(now.plusMinutes(RESERVATION_HOLD_MINUTES))
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            // Acquire exclusive row lock (SELECT ... FOR UPDATE) to serialize reservations
            TicketInventory inventory = ticketInventoryRepository.findForReservationWithLock(
                    request.getMatchId(), itemReq.getTicketCategoryId())
                    .orElseThrow(() -> new InsufficientInventoryException(
                            "Ticket category " + itemReq.getTicketCategoryId() + " not found for match " + request.getMatchId()));

            if (inventory.getAvailableQuantity() < itemReq.getQuantity()) {
                throw new InsufficientInventoryException("Not enough tickets available in " + inventory.getCategoryName()
                        + " (Requested: " + itemReq.getQuantity() + ", Available: " + inventory.getAvailableQuantity() + ")");
            }

            // Decrement available quantity with exclusive lock held
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - itemReq.getQuantity());
            ticketInventoryRepository.save(inventory);

            BigDecimal subtotal = inventory.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .ticketCategoryId(inventory.getTicketCategoryId())
                    .categoryName(inventory.getCategoryName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(inventory.getPrice())
                    .subtotal(subtotal)
                    .build();

            items.add(orderItem);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(items);

        Order savedOrder = orderRepository.save(order);
        log.info("Reserved order {} for user {} on match {} (expires at {})",
                savedOrder.getOrderNumber(), user.email(), request.getMatchId(), savedOrder.getExpiresAt());

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public RazorpayOrderResponse initiateRazorpayOrder(Long orderId, AuthenticatedUser user) {
        Order order = getOwnedOrderOrThrow(orderId, user);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order is not in PENDING state (current: " + order.getStatus() + ")");
        }
        if (order.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OrderExpiredException("Reservation has expired. Tickets have been released.");
        }

        RazorpayOrderResponse response = razorpayService.createOrder(order);
        order.setRazorpayOrderId(response.getRazorpayOrderId());
        orderRepository.save(order);
        return response;
    }

    @Transactional
    public OrderResponse confirmPayment(Long orderId, PaymentRequest paymentRequest, AuthenticatedUser user) {
        Order order = getOwnedOrderOrThrow(orderId, user);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order is not in PENDING state (current: " + order.getStatus() + ")");
        }

        if (order.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OrderExpiredException("Reservation has expired. Tickets have been released.");
        }

        if ("RAZORPAY".equalsIgnoreCase(paymentRequest.getPaymentMethod())) {
            if (paymentRequest.getRazorpaySignature() != null && !paymentRequest.getRazorpaySignature().isBlank()) {
                boolean valid = razorpayService.verifySignature(
                        order.getRazorpayOrderId(),
                        paymentRequest.getRazorpayPaymentId(),
                        paymentRequest.getRazorpaySignature()
                );
                if (!valid) {
                    throw new IllegalArgumentException("Invalid Razorpay payment signature");
                }
            }
            order.setRazorpayPaymentId(paymentRequest.getRazorpayPaymentId());
            order.setPaymentReference(paymentRequest.getPaymentReference() != null ? paymentRequest.getPaymentReference() : paymentRequest.getRazorpayPaymentId());
        } else {
            order.setPaymentReference(paymentRequest.getPaymentReference());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(paymentRequest.getPaymentMethod());

        Order saved = orderRepository.save(order);
        log.info("Confirmed order {} for user {} with payment ref {}", saved.getOrderNumber(), user.email(), saved.getPaymentReference());

        // Emit Kafka event for payment confirmation (consumable by Notification/Ticket Service)
        try {
            Map<String, Object> event = Map.of(
                    "orderId", saved.getId(),
                    "orderNumber", saved.getOrderNumber(),
                    "userId", saved.getUserId(),
                    "userEmail", saved.getUserEmail(),
                    "matchId", saved.getMatchId(),
                    "totalAmount", saved.getTotalAmount(),
                    "status", "CONFIRMED"
            );
            kafkaTemplate.send("ipl.order.confirmed", saved.getOrderNumber(), event);
            log.info("Emitted ipl.order.confirmed event to Kafka for order {}", saved.getOrderNumber());
        } catch (Exception ex) {
            log.warn("Kafka event publishing skipped (Kafka offline/reconnecting): {}", ex.getMessage());
        }

        return OrderResponse.fromEntity(saved);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, AuthenticatedUser user) {
        Order order = getOwnedOrderOrThrow(orderId, user);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only PENDING reservations can be cancelled (current: " + order.getStatus() + ")");
        }

        order.setStatus(OrderStatus.CANCELLED);

        // Return held tickets back to available inventory with exclusive lock
        for (OrderItem item : order.getItems()) {
            ticketInventoryRepository.findForReservationWithLock(order.getMatchId(), item.getTicketCategoryId())
                    .ifPresent(inventory -> {
                        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + item.getQuantity());
                        ticketInventoryRepository.save(inventory);
                    });
        }

        Order saved = orderRepository.save(order);
        log.info("Cancelled order {} and released tickets back to inventory", saved.getOrderNumber());

        // Emit Kafka event for ticket release
        try {
            kafkaTemplate.send("ipl.ticket.released", saved.getOrderNumber(), Map.of(
                    "orderId", saved.getId(),
                    "orderNumber", saved.getOrderNumber(),
                    "matchId", saved.getMatchId(),
                    "status", "CANCELLED"
            ));
        } catch (Exception ex) {
            log.warn("Kafka event publishing skipped: {}", ex.getMessage());
        }

        return OrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId, AuthenticatedUser user) {
        return OrderResponse.fromEntity(getOwnedOrderOrThrow(orderId, user));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(AuthenticatedUser user) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    @Transactional
    public List<InventoryResponse> getInventoryForMatch(Long matchId) {
        List<TicketInventory> list = ticketInventoryRepository.findByMatchId(matchId);
        if (list.isEmpty()) {
            initDefaultInventory(matchId);
            list = ticketInventoryRepository.findByMatchId(matchId);
        }
        return list.stream()
                .map(inv -> InventoryResponse.builder()
                        .id(inv.getId())
                        .matchId(inv.getMatchId())
                        .ticketCategoryId(inv.getTicketCategoryId())
                        .categoryName(inv.getCategoryName())
                        .price(inv.getPrice())
                        .totalQuantity(inv.getTotalQuantity())
                        .availableQuantity(inv.getAvailableQuantity())
                        .build())
                .toList();
    }

    private void initDefaultInventory(Long matchId) {
        TicketInventory vip = TicketInventory.builder()
                .matchId(matchId)
                .ticketCategoryId(1L)
                .categoryName("VIP Lounge")
                .price(BigDecimal.valueOf(15000.00))
                .totalQuantity(500)
                .availableQuantity(500)
                .build();

        TicketInventory premium = TicketInventory.builder()
                .matchId(matchId)
                .ticketCategoryId(2L)
                .categoryName("Premium Pavilion")
                .price(BigDecimal.valueOf(5000.00))
                .totalQuantity(2500)
                .availableQuantity(2500)
                .build();

        TicketInventory general = TicketInventory.builder()
                .matchId(matchId)
                .ticketCategoryId(3L)
                .categoryName("General Stand")
                .price(BigDecimal.valueOf(1500.00))
                .totalQuantity(25000)
                .availableQuantity(25000)
                .build();

        ticketInventoryRepository.saveAll(List.of(vip, premium, general));
        log.info("Initialized baseline ticket inventory for matchId={}", matchId);
    }

    private Order getOwnedOrderOrThrow(Long orderId, AuthenticatedUser user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.role());
        if (!isAdmin && !order.getUserId().equals(user.userId())) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        return order;
    }
}
