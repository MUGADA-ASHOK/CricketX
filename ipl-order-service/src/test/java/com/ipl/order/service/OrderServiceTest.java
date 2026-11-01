package com.ipl.order.service;

import com.ipl.order.dto.request.CreateOrderRequest;
import com.ipl.order.dto.request.PaymentRequest;
import com.ipl.order.dto.request.ReserveSeatsRequest;
import com.ipl.order.dto.request.TurnstileValidationRequest;
import com.ipl.order.dto.response.OrderResponse;
import com.ipl.order.dto.response.TurnstileValidationResponse;
import com.ipl.order.entity.*;
import com.ipl.order.enums.OrderStatus;
import com.ipl.order.enums.SeatStatus;
import com.ipl.order.enums.TicketStatus;
import com.ipl.order.exception.InsufficientInventoryException;
import com.ipl.order.repository.MatchSeatRepository;
import com.ipl.order.repository.OrderRepository;
import com.ipl.order.repository.TicketInventoryRepository;
import com.ipl.order.repository.TicketRepository;
import com.ipl.order.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketInventoryRepository ticketInventoryRepository;

    @Mock
    private MatchSeatRepository matchSeatRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RazorpayService razorpayService;

    @Mock
    private RedisFlashSaleService redisFlashSaleService;

    @Mock
    private QrCodeGeneratorService qrCodeGeneratorService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private AuthenticatedUser user;
    private TicketInventory vipInventory;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(10L, "fan@gmail.com", "ROLE_USER");

        vipInventory = TicketInventory.builder()
                .id(1L)
                .matchId(1L)
                .ticketCategoryId(1L)
                .categoryName("VIP Lounge")
                .price(BigDecimal.valueOf(15000.00))
                .totalQuantity(500)
                .availableQuantity(500)
                .build();
    }

    // =========================================================================
    // 1. Category-based Two-Phase Hold & Reserve Tests
    // =========================================================================

    @Test
    @DisplayName("Should successfully hold tickets and create PENDING order for 10 minutes")
    void testReserveTickets_Success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .matchId(1L)
                .items(List.of(new CreateOrderRequest.OrderItemRequest(1L, 2)))
                .build();

        when(redisFlashSaleService.tryReserveStock(1L, 1L, 2)).thenReturn(true);
        when(ticketInventoryRepository.findForReservationWithLock(1L, 1L)).thenReturn(Optional.of(vipInventory));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(101L);
            return o;
        });

        OrderResponse response = orderService.reserveTickets(request, user, "IDEM-KEY-001");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000.00));
        assertThat(vipInventory.getAvailableQuantity()).isEqualTo(498);
        verify(ticketInventoryRepository).save(vipInventory);
    }

    @Test
    @DisplayName("Should fast-reject reservation when Redis in-memory flash sale stock is exhausted")
    void testReserveTickets_RedisSoldOut_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .matchId(1L)
                .items(List.of(new CreateOrderRequest.OrderItemRequest(1L, 5)))
                .build();

        when(redisFlashSaleService.tryReserveStock(1L, 1L, 5)).thenReturn(false);

        assertThatThrownBy(() -> orderService.reserveTickets(request, user, null))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("Sold Out");

        // Verify DB row lock was NEVER acquired (Postgres completely protected)
        verifyNoInteractions(ticketInventoryRepository);
    }

    @Test
    @DisplayName("Should return existing order without decrementing stock when duplicate Idempotency-Key is provided")
    void testReserveTickets_IdempotencyKey_ReturnsExistingOrder() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .matchId(1L)
                .items(List.of(new CreateOrderRequest.OrderItemRequest(1L, 2)))
                .build();

        Order existingOrder = Order.builder()
                .id(99L)
                .orderNumber("ORD-EXISTING-01")
                .userId(user.userId())
                .userEmail(user.email())
                .matchId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(30000.00))
                .expiresAt(LocalDateTime.now().plusMinutes(8))
                .build();

        when(orderRepository.findByUserIdAndIdempotencyKey(user.userId(), "IDEM-KEY-DUP"))
                .thenReturn(Optional.of(existingOrder));

        OrderResponse response = orderService.reserveTickets(request, user, "IDEM-KEY-DUP");

        assertThat(response.getOrderNumber()).isEqualTo("ORD-EXISTING-01");
        verifyNoInteractions(redisFlashSaleService);
        verify(ticketInventoryRepository, never()).findForReservationWithLock(any(), any());
    }

    // =========================================================================
    // 2. Specific Seat Map Reservation Tests
    // =========================================================================

    @Test
    @DisplayName("Should successfully lease specific physical seats in Redis and set status to HELD")
    void testReserveSpecificSeats_Success() {
        ReserveSeatsRequest request = ReserveSeatsRequest.builder()
                .matchId(1L)
                .seatIds(List.of(1L, 2L))
                .build();

        MatchSeat seat1 = MatchSeat.builder()
                .id(1L).matchId(1L).ticketCategoryId(1L).categoryName("VIP Lounge")
                .seatNumber("VIP-A-1").price(BigDecimal.valueOf(15000.00)).status(SeatStatus.AVAILABLE).build();
        MatchSeat seat2 = MatchSeat.builder()
                .id(2L).matchId(1L).ticketCategoryId(1L).categoryName("VIP Lounge")
                .seatNumber("VIP-A-2").price(BigDecimal.valueOf(15000.00)).status(SeatStatus.AVAILABLE).build();

        when(redisFlashSaleService.tryHoldSeats(1L, List.of(1L, 2L), user.userId(), 10)).thenReturn(true);
        when(matchSeatRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(seat1, seat2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(201L);
            return o;
        });

        OrderResponse response = orderService.reserveSpecificSeats(request, user);

        assertThat(response).isNotNull();
        assertThat(seat1.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat2.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat1.getHeldByUserId()).isEqualTo(user.userId());
        verify(matchSeatRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should reject and rollback when selected seat is already held by another fan")
    void testReserveSpecificSeats_SeatContested_ThrowsException() {
        ReserveSeatsRequest request = ReserveSeatsRequest.builder()
                .matchId(1L)
                .seatIds(List.of(1L, 2L))
                .build();

        when(redisFlashSaleService.tryHoldSeats(1L, List.of(1L, 2L), user.userId(), 10)).thenReturn(false);

        assertThatThrownBy(() -> orderService.reserveSpecificSeats(request, user))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("currently held by another user or already booked");

        verifyNoInteractions(matchSeatRepository);
    }

    // =========================================================================
    // 3. Payment Confirmation, Signature Verification & Anti-Fraud Tests
    // =========================================================================

    @Test
    @DisplayName("Should confirm order and issue digital QR tickets when Razorpay HMAC signature is valid")
    void testConfirmPayment_Razorpay_Success() {
        Order pendingOrder = Order.builder()
                .id(301L)
                .orderNumber("ORD-PAY-01")
                .userId(user.userId())
                .userEmail(user.email())
                .matchId(1L)
                .status(OrderStatus.PENDING)
                .razorpayOrderId("order_rzp_123")
                .totalAmount(BigDecimal.valueOf(30000.00))
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .items(new ArrayList<>())
                .build();

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentMethod("RAZORPAY")
                .razorpayOrderId("order_rzp_123")
                .razorpayPaymentId("pay_rzp_456")
                .razorpaySignature("valid_signature_hash")
                .build();

        when(orderRepository.findById(301L)).thenReturn(Optional.of(pendingOrder));
        when(razorpayService.verifySignature("order_rzp_123", "pay_rzp_456", "valid_signature_hash")).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(matchSeatRepository.findByOrderId(301L)).thenReturn(Collections.emptyList());
        when(qrCodeGeneratorService.generateSignedPayload(anyString(), anyLong(), anyString())).thenReturn("IPL2026|PAYLOAD|SIG:HASH");
        when(qrCodeGeneratorService.generateQrCodeBase64(anyString())).thenReturn("data:image/png;base64,sample");

        OrderResponse response = orderService.confirmPayment(301L, paymentRequest, user);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(pendingOrder.getPaymentReference()).isEqualTo("pay_rzp_456");
        verify(kafkaTemplate).send(eq("ipl.order.confirmed"), anyString(), anyMap());
    }

    @Test
    @DisplayName("Should strictly reject payment confirmation when Razorpay HMAC signature is invalid")
    void testConfirmPayment_Razorpay_InvalidSignature_ThrowsException() {
        Order pendingOrder = Order.builder()
                .id(302L)
                .userId(user.userId())
                .status(OrderStatus.PENDING)
                .razorpayOrderId("order_rzp_123")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentMethod("RAZORPAY")
                .razorpayPaymentId("pay_rzp_fake")
                .razorpaySignature("forged_signature")
                .build();

        when(orderRepository.findById(302L)).thenReturn(Optional.of(pendingOrder));
        when(razorpayService.verifySignature(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> orderService.confirmPayment(302L, paymentRequest, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Razorpay payment signature");
    }

    @Test
    @DisplayName("Should reject UPI confirmation if payment reference has already been used on another confirmed order")
    void testConfirmPayment_UPI_ReusedReference_AntiFraud_Rejected() {
        Order pendingOrder = Order.builder()
                .id(303L)
                .userId(user.userId())
                .status(OrderStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentMethod("UPI")
                .paymentReference("UPI_UTR_ALREADY_USED_123")
                .build();

        when(orderRepository.findById(303L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.existsByPaymentReferenceAndStatus("UPI_UTR_ALREADY_USED_123", OrderStatus.CONFIRMED))
                .thenReturn(true);

        assertThatThrownBy(() -> orderService.confirmPayment(303L, paymentRequest, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate payments rejected");
    }

    @Test
    @DisplayName("Should return existing confirmed order safely when confirmPayment is called multiple times (Idempotent)")
    void testConfirmPayment_Idempotent_AlreadyConfirmed() {
        Order confirmedOrder = Order.builder()
                .id(304L)
                .userId(user.userId())
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.valueOf(15000.00))
                .orderNumber("ORD-CONFIRMED-01")
                .build();

        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentMethod("RAZORPAY")
                .razorpayPaymentId("pay_123")
                .razorpaySignature("sig_123")
                .build();

        when(orderRepository.findById(304L)).thenReturn(Optional.of(confirmedOrder));

        OrderResponse response = orderService.confirmPayment(304L, paymentRequest, user);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(razorpayService);
        verify(orderRepository, never()).save(any());
    }

    // =========================================================================
    // 4. Cancellation & Inventory Restoration Tests
    // =========================================================================

    @Test
    @DisplayName("Should cancel reservation, restore category stock to DB and Redis, and free seats")
    void testCancelOrder_Success() {
        Order order = Order.builder()
                .id(401L)
                .userId(user.userId())
                .orderNumber("ORD-CANCEL-01")
                .status(OrderStatus.PENDING)
                .matchId(1L)
                .items(List.of(OrderItem.builder().ticketCategoryId(1L).quantity(2).build()))
                .build();

        MatchSeat seat = MatchSeat.builder().id(11L).status(SeatStatus.HELD).build();

        when(orderRepository.findById(401L)).thenReturn(Optional.of(order));
        when(ticketInventoryRepository.findForReservationWithLock(1L, 1L)).thenReturn(Optional.of(vipInventory));
        when(matchSeatRepository.findByOrderId(401L)).thenReturn(List.of(seat));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(401L, user);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(redisFlashSaleService).releaseStock(1L, 1L, 2);
        verify(redisFlashSaleService).releaseSeats(eq(1L), anyList());
        verify(kafkaTemplate).send(eq("ipl.ticket.released"), anyString(), anyMap());
    }

    // =========================================================================
    // 5. Turnstile Gate Validation & Security Tests
    // =========================================================================

    @Test
    @DisplayName("Should grant entrance at turnstile for authentic, unused QR ticket")
    void testValidateTurnstile_Success() {
        String qrPayload = "IPL2026|TKT-001|1|VIP-A-1|SIG:VALID_HASH";
        Ticket ticket = Ticket.builder()
                .id(501L)
                .ticketNumber("TKT-001")
                .matchId(1L)
                .seatNumber("VIP-A-1")
                .stand("VIP Lounge")
                .status(TicketStatus.ACTIVE)
                .qrCodeData(qrPayload)
                .build();

        TurnstileValidationRequest request = TurnstileValidationRequest.builder()
                .qrCodeData(qrPayload)
                .gate("Gate 3A")
                .build();

        when(qrCodeGeneratorService.verifyPayload(qrPayload)).thenReturn(true);
        when(ticketRepository.findByQrCodeData(qrPayload)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TurnstileValidationResponse response = orderService.validateTurnstile(request);

        assertThat(response.getResult()).isEqualTo("ALLOWED");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(ticket.getTurnstileGate()).isEqualTo("Gate 3A");
    }

    @Test
    @DisplayName("Should reject and block entrance when ticket has already been scanned (Duplicate Entry Defense)")
    void testValidateTurnstile_DuplicateScan_Denied() {
        String qrPayload = "IPL2026|TKT-001|1|VIP-A-1|SIG:VALID_HASH";
        Ticket usedTicket = Ticket.builder()
                .id(501L)
                .ticketNumber("TKT-001")
                .status(TicketStatus.USED)
                .scannedAt(LocalDateTime.now().minusMinutes(15))
                .turnstileGate("Gate 1B")
                .qrCodeData(qrPayload)
                .build();

        TurnstileValidationRequest request = TurnstileValidationRequest.builder()
                .qrCodeData(qrPayload)
                .gate("Gate 4C")
                .build();

        when(qrCodeGeneratorService.verifyPayload(qrPayload)).thenReturn(true);
        when(ticketRepository.findByQrCodeData(qrPayload)).thenReturn(Optional.of(usedTicket));

        TurnstileValidationResponse response = orderService.validateTurnstile(request);

        assertThat(response.getResult()).isEqualTo("DENIED");
        assertThat(response.getMessage()).contains("DUPLICATE ENTRY DETECTED");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should deny entrance with security alert when QR code payload HMAC signature is tampered/forged")
    void testValidateTurnstile_ForgedQR_Denied() {
        String forgedPayload = "IPL2026|TKT-FAKE|1|VIP-A-1|SIG:INVALID";
        TurnstileValidationRequest request = TurnstileValidationRequest.builder()
                .qrCodeData(forgedPayload)
                .gate("Gate 1A")
                .build();

        when(qrCodeGeneratorService.verifyPayload(forgedPayload)).thenReturn(false);

        TurnstileValidationResponse response = orderService.validateTurnstile(request);

        assertThat(response.getResult()).isEqualTo("DENIED");
        assertThat(response.getMessage()).contains("SECURITY ALERT: Forged or invalid ticket QR Code");
        verifyNoInteractions(ticketRepository);
    }
}

