package com.ipl.notification.controller;

import com.ipl.notification.dto.request.BroadcastRequest;
import com.ipl.notification.dto.response.ApiResponse;
import com.ipl.notification.dto.response.NotificationResponse;
import com.ipl.notification.security.AuthenticatedUser;
import com.ipl.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get all in-app notifications for the authenticated user (newest first).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            @AuthenticationPrincipal AuthenticatedUser user) {
        List<NotificationResponse> list = notificationService.getUserNotifications(user.userId());
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully", list));
    }

    /**
     * Get unread notification badge count.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser user) {
        long count = notificationService.getUnreadCount(user.userId());
        return ResponseEntity.ok(ApiResponse.success("Unread count", Map.of("unreadCount", count)));
    }

    /**
     * Mark a specific notification as read.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        NotificationResponse response = notificationService.markAsRead(id, user.userId());
        return ResponseEntity.ok(ApiResponse.success("Marked as read", response));
    }

    /**
     * Mark all notifications as read.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @AuthenticationPrincipal AuthenticatedUser user) {
        int updated = notificationService.markAllAsRead(user.userId());
        return ResponseEntity.ok(ApiResponse.success("Marked all as read", Map.of("updatedCount", updated)));
    }

    /**
     * Admin broadcast announcement to all fans.
     */
    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationResponse>> broadcast(
            @Valid @RequestBody BroadcastRequest request) {
        NotificationResponse response = notificationService.broadcastAnnouncement(request.getTitle(), request.getMessage());
        return ResponseEntity.ok(ApiResponse.success("Broadcast announcement sent", response));
    }
}

