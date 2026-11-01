package com.ipl.order.controller;

import com.ipl.order.dto.request.CreateOrderRequest;
import com.ipl.order.dto.request.PaymentRequest;
import com.ipl.order.dto.response.ApiResponse;
import com.ipl.order.dto.response.InventoryResponse;
import com.ipl.order.dto.response.OrderResponse;
import com.ipl.order.security.AuthenticatedUser;
import com.ipl.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Phase 1 of Two-Phase Booking: Hold seats for 10 minutes.
     * Supports Idempotency-Key header or request body field to avoid duplicate seat holds.
     */
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<OrderResponse>> reserveTickets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody CreateOrderRequest request) {
        String idempotencyKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : request.getIdempotencyKey();
        OrderResponse response = orderService.reserveTickets(request, user, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tickets held successfully. Please complete payment within 10 minutes.", response));
    }

    /**
     * Phase 2a: Initiate a Razorpay payment order.
     */
    @PostMapping("/{orderId}/razorpay/create")
    public ResponseEntity<ApiResponse<com.ipl.order.dto.response.RazorpayOrderResponse>> createRazorpayOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        com.ipl.order.dto.response.RazorpayOrderResponse response = orderService.initiateRazorpayOrder(orderId, user);
        return ResponseEntity.ok(ApiResponse.success("Razorpay payment order created successfully.", response));
    }

    /**
     * Phase 2: Confirm payment and finalize order.
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<ApiResponse<OrderResponse>> payOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PaymentRequest request) {
        OrderResponse response = orderService.confirmPayment(orderId, request, user);
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed! Your match tickets are booked.", response));
    }

    /**
     * Cancel a pending reservation and release tickets back immediately.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse response = orderService.cancelOrder(orderId, user);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled and tickets released.", response));
    }

    /**
     * Get details of a single order.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        OrderResponse response = orderService.getOrder(orderId, user);
        return ResponseEntity.ok(ApiResponse.success("Order details", response));
    }

    /**
     * List all orders placed by the current authenticated user.
     */
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal AuthenticatedUser user) {
        List<OrderResponse> orders = orderService.getMyOrders(user);
        return ResponseEntity.ok(ApiResponse.success("My orders", orders));
    }

    /**
     * Check live ticket availability and category pricing for a match.
     */
    @GetMapping("/inventory/{matchId}")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getInventory(
            @PathVariable Long matchId) {
        List<InventoryResponse> inventory = orderService.getInventoryForMatch(matchId);
        return ResponseEntity.ok(ApiResponse.success("Match ticket inventory", inventory));
    }

    /**
     * Live Seat Map: Get layout of physical seats and their real-time statuses (AVAILABLE, HELD, BOOKED).
     */
    @GetMapping("/matches/{matchId}/seats")
    public ResponseEntity<ApiResponse<List<com.ipl.order.dto.response.SeatResponse>>> getSeatMap(
            @PathVariable Long matchId) {
        List<com.ipl.order.dto.response.SeatResponse> seats = orderService.getSeatsForMatch(matchId);
        return ResponseEntity.ok(ApiResponse.success("Seat layout map", seats));
    }

    /**
     * Specific Seat Allocation: Hold specific chosen seats for 10 minutes.
     */
    @PostMapping("/reserve-seats")
    public ResponseEntity<ApiResponse<OrderResponse>> reserveSpecificSeats(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody com.ipl.order.dto.request.ReserveSeatsRequest request) {
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            request = com.ipl.order.dto.request.ReserveSeatsRequest.builder()
                    .matchId(request.getMatchId())
                    .seatIds(request.getSeatIds())
                    .idempotencyKey(idempotencyKeyHeader)
                    .build();
        }
        OrderResponse response = orderService.reserveSpecificSeats(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seats held successfully for 10 minutes.", response));
    }

    /**
     * Digital Ticket Wallet: View all digital tickets with their QR codes.
     */
    @GetMapping("/tickets/my-tickets")
    public ResponseEntity<ApiResponse<List<com.ipl.order.dto.response.TicketResponse>>> getMyTickets(
            @AuthenticationPrincipal AuthenticatedUser user) {
        List<com.ipl.order.dto.response.TicketResponse> tickets = orderService.getMyTickets(user);
        return ResponseEntity.ok(ApiResponse.success("My digital tickets", tickets));
    }

    /**
     * Turnstile Gate Validation:
     * Scans QR Code at stadium gate, verifies cryptographic HMAC signature,
     * and prevents duplicate entries.
     */
    @PostMapping("/tickets/validate-turnstile")
    public ResponseEntity<ApiResponse<com.ipl.order.dto.response.TurnstileValidationResponse>> validateTurnstile(
            @Valid @RequestBody com.ipl.order.dto.request.TurnstileValidationRequest request) {
        com.ipl.order.dto.response.TurnstileValidationResponse response = orderService.validateTurnstile(request);
        return ResponseEntity.ok(ApiResponse.success("Turnstile scan result: " + response.getResult(), response));
    }
}
