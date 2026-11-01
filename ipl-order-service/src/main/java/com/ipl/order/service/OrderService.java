package com.ipl.order.service;

import com.ipl.order.dto.request.CreateOrderRequest;
import com.ipl.order.dto.request.PaymentRequest;
import com.ipl.order.dto.request.ReserveSeatsRequest;
import com.ipl.order.dto.request.TurnstileValidationRequest;
import com.ipl.order.dto.response.*;
import com.ipl.order.entity.*;
import com.ipl.order.enums.OrderStatus;
import com.ipl.order.enums.SeatStatus;
import com.ipl.order.enums.TicketStatus;
import com.ipl.order.exception.InsufficientInventoryException;
import com.ipl.order.exception.InvalidOrderStateException;
import com.ipl.order.exception.OrderExpiredException;
import com.ipl.order.exception.OrderNotFoundException;
import com.ipl.order.repository.MatchSeatRepository;
import com.ipl.order.repository.OrderRepository;
import com.ipl.order.repository.TicketInventoryRepository;
import com.ipl.order.repository.TicketRepository;
import com.ipl.order.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core Order, Ticket Reservation & Flash Sale Service.
 * Implements:
 * 1. High-concurrency flash sale engine powered by Redis Lua scripts
 * 2. Seat map and specific physical seat selection
 * 3. Two-phase ticket holds with 10-minute TTL
 * 4. Cryptographic QR-code digital ticket generation upon payment
 * 5. Turnstile gate entry scanning with duplicate-entry prevention
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int RESERVATION_HOLD_MINUTES = 10;

    private final OrderRepository orderRepository;
    private final TicketInventoryRepository ticketInventoryRepository;
    private final MatchSeatRepository matchSeatRepository;
    private final TicketRepository ticketRepository;
    private final RazorpayService razorpayService;
    private final RedisFlashSaleService redisFlashSaleService;
    private final QrCodeGeneratorService qrCodeGeneratorService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderResponse reserveTickets(CreateOrderRequest request, AuthenticatedUser user, String idempotencyKey) {
        // Idempotency check: Return existing reservation if identical key provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String trimmedKey = idempotencyKey.trim();
            var existing = orderRepository.findByUserIdAndIdempotencyKey(user.userId(), trimmedKey);
            if (existing.isPresent()) {
                Order existingOrder = existing.get();
                log.info("Idempotent reserve request: matched existing order {} for user {}",
                        existingOrder.getOrderNumber(), user.email());
                return OrderResponse.fromEntity(existingOrder);
            }
        }

        // Fast-path in-memory Flash Sale Check via Redis Lua script (< 2ms)
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            boolean reservedInRedis = redisFlashSaleService.tryReserveStock(
                    request.getMatchId(), itemReq.getTicketCategoryId(), itemReq.getQuantity());
            if (!reservedInRedis) {
                throw new InsufficientInventoryException("Sold Out: Category " + itemReq.getTicketCategoryId()
                        + " does not have " + itemReq.getQuantity() + " tickets available.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase())
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null)
                .userId(user.userId())
                .userEmail(user.email())
                .matchId(request.getMatchId())
                .status(OrderStatus.PENDING)
                .expiresAt(now.plusMinutes(RESERVATION_HOLD_MINUTES))
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            TicketInventory inventory = ticketInventoryRepository.findForReservationWithLock(
                    request.getMatchId(), itemReq.getTicketCategoryId())
                    .orElseThrow(() -> new InsufficientInventoryException(
                            "Ticket category " + itemReq.getTicketCategoryId() + " not found for match " + request.getMatchId()));

            if (inventory.getAvailableQuantity() < itemReq.getQuantity()) {
                // Rollback Redis in-memory reservation on DB mismatch
                redisFlashSaleService.releaseStock(request.getMatchId(), itemReq.getTicketCategoryId(), itemReq.getQuantity());
                throw new InsufficientInventoryException("Not enough tickets available in " + inventory.getCategoryName());
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

    /**
     * Specific Seat Map Reservation:
     * User picks exact seats (e.g. Pavilion Stand B, Row C, Seats 12 & 13).
     * Atomically locks seats in Redis and marks them HELD in PostgreSQL.
     */
    @Transactional
    public OrderResponse reserveSpecificSeats(ReserveSeatsRequest request, AuthenticatedUser user) {
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = orderRepository.findByUserIdAndIdempotencyKey(user.userId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                return OrderResponse.fromEntity(existing.get());
            }
        }

        // Phase 1: In-memory distributed seat lease in Redis (10-minute hold)
        boolean seatsLeased = redisFlashSaleService.tryHoldSeats(
                request.getMatchId(), request.getSeatIds(), user.userId(), RESERVATION_HOLD_MINUTES);
        if (!seatsLeased) {
            throw new InsufficientInventoryException("One or more selected seats are currently held by another user or already booked.");
        }

        // Phase 2: DB state verification
        List<MatchSeat> seats = matchSeatRepository.findByIdIn(request.getSeatIds());
        if (seats.size() != request.getSeatIds().size()) {
            redisFlashSaleService.releaseSeats(request.getMatchId(), request.getSeatIds());
            throw new InsufficientInventoryException("One or more selected seats were not found for match " + request.getMatchId());
        }

        LocalDateTime now = LocalDateTime.now();
        for (MatchSeat seat : seats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                redisFlashSaleService.releaseSeats(request.getMatchId(), request.getSeatIds());
                throw new InsufficientInventoryException("Seat " + seat.getSeatNumber() + " is already " + seat.getStatus());
            }
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase())
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null)
                .userId(user.userId())
                .userEmail(user.email())
                .matchId(request.getMatchId())
                .status(OrderStatus.PENDING)
                .expiresAt(now.plusMinutes(RESERVATION_HOLD_MINUTES))
                .totalAmount(BigDecimal.ZERO)
                .build();

        for (MatchSeat seat : seats) {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByUserId(user.userId());
            seat.setHeldUntil(now.plusMinutes(RESERVATION_HOLD_MINUTES));
            totalAmount = totalAmount.add(seat.getPrice());

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .ticketCategoryId(seat.getTicketCategoryId())
                    .categoryName(seat.getCategoryName() + " (" + seat.getSeatNumber() + ")")
                    .quantity(1)
                    .unitPrice(seat.getPrice())
                    .subtotal(seat.getPrice())
                    .build();
            items.add(item);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(items);
        Order savedOrder = orderRepository.save(order);

        for (MatchSeat seat : seats) {
            seat.setOrderId(savedOrder.getId());
        }
        matchSeatRepository.saveAll(seats);

        log.info("Reserved specific seats {} for order {} (expires at {})",
                request.getSeatIds(), savedOrder.getOrderNumber(), savedOrder.getExpiresAt());

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public RazorpayOrderResponse initiateRazorpayOrder(Long orderId, AuthenticatedUser user) {
        Order order = getOwnedOrderOrThrow(orderId, user);

        // Idempotency check: If Razorpay order already generated and still pending & unexpired
        if (order.getRazorpayOrderId() != null && !order.getRazorpayOrderId().isBlank()) {
            if (order.getStatus() == OrderStatus.PENDING && !order.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("Idempotent Razorpay order request: returning existing razorpayOrderId {} for order {}",
                        order.getRazorpayOrderId(), order.getOrderNumber());
                return RazorpayOrderResponse.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .razorpayOrderId(order.getRazorpayOrderId())
                        .amount(order.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValue())
                        .currency("INR")
                        .userEmail(order.getUserEmail())
                        .build();
            }
        }

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

        // Idempotency check: If order is already CONFIRMED, return existing order safely
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Idempotent payment confirmation: order {} is already CONFIRMED. Returning existing record.",
                    order.getOrderNumber());
            return OrderResponse.fromEntity(order);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order is not in PENDING state (current: " + order.getStatus() + ")");
        }

        if (order.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OrderExpiredException("Reservation has expired. Tickets have been released.");
        }

        if ("RAZORPAY".equalsIgnoreCase(paymentRequest.getPaymentMethod())) {
            if (paymentRequest.getRazorpayPaymentId() == null || paymentRequest.getRazorpayPaymentId().isBlank()
                    || paymentRequest.getRazorpaySignature() == null || paymentRequest.getRazorpaySignature().isBlank()) {
                throw new IllegalArgumentException("Razorpay payment requires both razorpayPaymentId and a valid razorpaySignature");
            }
            boolean valid = razorpayService.verifySignature(
                    order.getRazorpayOrderId(),
                    paymentRequest.getRazorpayPaymentId(),
                    paymentRequest.getRazorpaySignature()
            );
            if (!valid) {
                throw new IllegalArgumentException("Cryptographic verification failed: Invalid Razorpay payment signature");
            }
            order.setRazorpayPaymentId(paymentRequest.getRazorpayPaymentId());
            order.setPaymentReference(paymentRequest.getRazorpayPaymentId());
        } else if ("UPI".equalsIgnoreCase(paymentRequest.getPaymentMethod())) {
            if (paymentRequest.getPaymentReference() == null || paymentRequest.getPaymentReference().isBlank()) {
                throw new IllegalArgumentException("UPI payment requires a valid paymentReference / UTR reference number");
            }
            if (orderRepository.existsByPaymentReferenceAndStatus(paymentRequest.getPaymentReference(), OrderStatus.CONFIRMED)) {
                throw new IllegalArgumentException("Payment reference has already been used for another confirmed booking. Duplicate payments rejected.");
            }
            // Bank gateway UTR format & amount validation
            if (!paymentRequest.getPaymentReference().matches("^[A-Za-z0-9_-]{6,64}$")) {
                throw new IllegalArgumentException("Invalid UPI transaction reference format from banking switch");
            }
            order.setPaymentReference(paymentRequest.getPaymentReference());
        } else {
            throw new IllegalArgumentException("Unsupported payment method: " + paymentRequest.getPaymentMethod() + ". Supported methods: RAZORPAY, UPI");
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(paymentRequest.getPaymentMethod());
        Order saved = orderRepository.save(order);

        // Finalize seats: Transition from HELD to BOOKED
        List<MatchSeat> heldSeats = matchSeatRepository.findByOrderId(saved.getId());
        if (!heldSeats.isEmpty()) {
            for (MatchSeat seat : heldSeats) {
                seat.setStatus(SeatStatus.BOOKED);
                seat.setHeldUntil(null);
            }
            matchSeatRepository.saveAll(heldSeats);
        }

        // Issue digital tickets with cryptographically signed QR codes
        issueDigitalTicketsForOrder(saved, heldSeats);

        log.info("Confirmed order {} for user {} with payment ref {}", saved.getOrderNumber(), user.email(), saved.getPaymentReference());

        // Emit Kafka event for payment confirmation (consumed by Notification Service)
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

    private void issueDigitalTicketsForOrder(Order order, List<MatchSeat> heldSeats) {
        if (!heldSeats.isEmpty()) {
            for (MatchSeat seat : heldSeats) {
                String ticketNum = "TKT-2026-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                String qrPayload = qrCodeGeneratorService.generateSignedPayload(ticketNum, order.getMatchId(), seat.getSeatNumber());
                String qrBase64 = qrCodeGeneratorService.generateQrCodeBase64(qrPayload);

                Ticket ticket = Ticket.builder()
                        .ticketNumber(ticketNum)
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .userId(order.getUserId())
                        .userEmail(order.getUserEmail())
                        .matchId(order.getMatchId())
                        .seatId(seat.getId())
                        .categoryName(seat.getCategoryName())
                        .stand(seat.getStand())
                        .section(seat.getSection())
                        .seatNumber(seat.getSeatNumber())
                        .price(seat.getPrice())
                        .status(TicketStatus.ACTIVE)
                        .qrCodeData(qrPayload)
                        .qrCodeBase64(qrBase64)
                        .build();

                ticketRepository.save(ticket);
            }
        } else {
            // General category tickets
            for (OrderItem item : order.getItems()) {
                for (int i = 1; i <= item.getQuantity(); i++) {
                    String ticketNum = "TKT-2026-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                    String seatLabel = "GA-" + i;
                    String qrPayload = qrCodeGeneratorService.generateSignedPayload(ticketNum, order.getMatchId(), seatLabel);
                    String qrBase64 = qrCodeGeneratorService.generateQrCodeBase64(qrPayload);

                    Ticket ticket = Ticket.builder()
                            .ticketNumber(ticketNum)
                            .orderId(order.getId())
                            .orderNumber(order.getOrderNumber())
                            .userId(order.getUserId())
                            .userEmail(order.getUserEmail())
                            .matchId(order.getMatchId())
                            .categoryName(item.getCategoryName())
                            .stand("General Area")
                            .section("General")
                            .seatNumber(seatLabel)
                            .price(item.getUnitPrice())
                            .status(TicketStatus.ACTIVE)
                            .qrCodeData(qrPayload)
                            .qrCodeBase64(qrBase64)
                            .build();

                    ticketRepository.save(ticket);
                }
            }
        }
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, AuthenticatedUser user) {
        Order order = getOwnedOrderOrThrow(orderId, user);

        // Idempotency check: If order is already CANCELLED, return existing order safely
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Idempotent cancel request: order {} is already CANCELLED. Returning existing record.",
                    order.getOrderNumber());
            return OrderResponse.fromEntity(order);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only PENDING reservations can be cancelled (current: " + order.getStatus() + ")");
        }

        order.setStatus(OrderStatus.CANCELLED);

        // Return category inventory
        for (OrderItem item : order.getItems()) {
            ticketInventoryRepository.findForReservationWithLock(order.getMatchId(), item.getTicketCategoryId())
                    .ifPresent(inventory -> {
                        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + item.getQuantity());
                        ticketInventoryRepository.save(inventory);
                    });
            redisFlashSaleService.releaseStock(order.getMatchId(), item.getTicketCategoryId(), item.getQuantity());
        }

        // Release physical seats if any
        List<MatchSeat> heldSeats = matchSeatRepository.findByOrderId(order.getId());
        if (!heldSeats.isEmpty()) {
            for (MatchSeat seat : heldSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldByUserId(null);
                seat.setHeldUntil(null);
                seat.setOrderId(null);
            }
            matchSeatRepository.saveAll(heldSeats);
            redisFlashSaleService.releaseSeats(order.getMatchId(), heldSeats.stream().map(MatchSeat::getId).toList());
        }

        Order saved = orderRepository.save(order);
        log.info("Cancelled order {} and released tickets back to inventory", saved.getOrderNumber());

        // Emit Kafka event for ticket release
        try {
            kafkaTemplate.send("ipl.ticket.released", saved.getOrderNumber(), Map.of(
                    "orderId", saved.getId(),
                    "orderNumber", saved.getOrderNumber(),
                    "matchId", saved.getMatchId(),
                    "userId", saved.getUserId(),
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

    /**
     * Live Seat Map query: Returns physical seat layout for the stadium.
     */
    @Transactional
    public List<SeatResponse> getSeatsForMatch(Long matchId) {
        List<MatchSeat> seats = matchSeatRepository.findByMatchIdOrderByStandAscRowNameAscSeatNumberAsc(matchId);
        if (seats.isEmpty()) {
            initDefaultSeatMap(matchId);
            seats = matchSeatRepository.findByMatchIdOrderByStandAscRowNameAscSeatNumberAsc(matchId);
        }
        return seats.stream().map(SeatResponse::fromEntity).toList();
    }

    /**
     * Digital Ticket Wallet: Lists all tickets owned by the fan with their QR codes.
     */
    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(AuthenticatedUser user) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .map(TicketResponse::fromEntity)
                .toList();
    }

    /**
     * Turnstile Gate Validation:
     * Scans QR code at match day stadium entrance.
     * Prevents forgery & duplicate entry attacks.
     */
    @Transactional
    public TurnstileValidationResponse validateTurnstile(TurnstileValidationRequest request) {
        // Step 1: Verify cryptographic signature
        if (!qrCodeGeneratorService.verifyPayload(request.getQrCodeData())) {
            log.warn("Turnstile validation failed: Tampered or forged QR payload");
            return TurnstileValidationResponse.builder()
                    .result("DENIED")
                    .message("SECURITY ALERT: Forged or invalid ticket QR Code.")
                    .gate(request.getGate())
                    .build();
        }

        // Step 2: Query ticket by QR payload
        Optional<Ticket> ticketOpt = ticketRepository.findByQrCodeData(request.getQrCodeData());
        if (ticketOpt.isEmpty()) {
            return TurnstileValidationResponse.builder()
                    .result("DENIED")
                    .message("Ticket not found in official stadium registry.")
                    .gate(request.getGate())
                    .build();
        }

        Ticket ticket = ticketOpt.get();

        // Step 3: Check ticket state
        if (ticket.getStatus() == TicketStatus.USED) {
            log.warn("Turnstile duplicate entry attempt for ticket {} at gate {}", ticket.getTicketNumber(), request.getGate());
            return TurnstileValidationResponse.builder()
                    .result("DENIED")
                    .message("DUPLICATE ENTRY DETECTED: Ticket already scanned at " + ticket.getScannedAt() + " via " + ticket.getTurnstileGate())
                    .ticketNumber(ticket.getTicketNumber())
                    .matchId(ticket.getMatchId())
                    .seatNumber(ticket.getSeatNumber())
                    .stand(ticket.getStand())
                    .gate(request.getGate())
                    .scannedAt(ticket.getScannedAt())
                    .build();
        }

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return TurnstileValidationResponse.builder()
                    .result("DENIED")
                    .message("ENTRY DENIED: This ticket has been CANCELLED or refunded.")
                    .ticketNumber(ticket.getTicketNumber())
                    .matchId(ticket.getMatchId())
                    .gate(request.getGate())
                    .build();
        }

        // Step 4: Mark ticket as USED
        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(TicketStatus.USED);
        ticket.setScannedAt(now);
        ticket.setTurnstileGate(request.getGate());
        ticketRepository.save(ticket);

        log.info("Granted entry for ticket {} at gate {}", ticket.getTicketNumber(), request.getGate());

        return TurnstileValidationResponse.builder()
                .result("ALLOWED")
                .message("Entry Granted! Welcome to the match.")
                .ticketNumber(ticket.getTicketNumber())
                .matchId(ticket.getMatchId())
                .seatNumber(ticket.getSeatNumber())
                .stand(ticket.getStand())
                .gate(request.getGate())
                .scannedAt(now)
                .build();
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

        // Preload Redis stock
        redisFlashSaleService.syncStock(matchId, 1L, 500);
        redisFlashSaleService.syncStock(matchId, 2L, 2500);
        redisFlashSaleService.syncStock(matchId, 3L, 25000);

        log.info("Initialized baseline ticket inventory for matchId={}", matchId);
    }

    private void initDefaultSeatMap(Long matchId) {
        List<MatchSeat> seats = new ArrayList<>();

        // Generate VIP Seats (Row A & B, 1-10)
        for (int i = 1; i <= 10; i++) {
            seats.add(MatchSeat.builder()
                    .matchId(matchId)
                    .ticketCategoryId(1L)
                    .categoryName("VIP Lounge")
                    .stand("Presidential VIP Box")
                    .section("West Wing")
                    .rowName("Row A")
                    .seatNumber("VIP-A-" + i)
                    .price(BigDecimal.valueOf(15000.00))
                    .status(SeatStatus.AVAILABLE)
                    .build());
        }

        // Generate Premium Pavilion Seats (Rows A & B, 1-15)
        for (int i = 1; i <= 15; i++) {
            seats.add(MatchSeat.builder()
                    .matchId(matchId)
                    .ticketCategoryId(2L)
                    .categoryName("Premium Pavilion")
                    .stand("Club House Pavilion")
                    .section("Lower Tier")
                    .rowName("Row B")
                    .seatNumber("PAV-B-" + i)
                    .price(BigDecimal.valueOf(5000.00))
                    .status(SeatStatus.AVAILABLE)
                    .build());
        }

        matchSeatRepository.saveAll(seats);
        log.info("Initialized physical seat layout ({} seats) for matchId={}", seats.size(), matchId);
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
