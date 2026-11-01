package com.ipl.notification.service;

import com.ipl.notification.dto.response.NotificationResponse;
import com.ipl.notification.entity.InAppNotification;
import com.ipl.notification.enums.NotificationType;
import com.ipl.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private InAppNotification sampleNotification;

    @BeforeEach
    void setUp() {
        sampleNotification = InAppNotification.builder()
                .id(1L)
                .userId(10L)
                .title("Booking Confirmed")
                .message("Your order ORD-001 has been confirmed.")
                .type(NotificationType.ORDER_CONFIRMED)
                .referenceType("ORDER")
                .referenceId("ORD-001")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should fetch all notifications for authenticated user")
    void testGetUserNotifications() {
        when(notificationRepository.findForUser(10L)).thenReturn(List.of(sampleNotification));

        List<NotificationResponse> list = notificationService.getUserNotifications(10L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("Booking Confirmed");
    }

    @Test
    @DisplayName("Should return accurate unread badge count")
    void testGetUnreadCount() {
        when(notificationRepository.countUnreadForUser(10L)).thenReturn(3L);

        long count = notificationService.getUnreadCount(10L);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should mark single notification as read")
    void testMarkAsRead() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(sampleNotification));
        when(notificationRepository.save(any(InAppNotification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(1L, 10L);

        assertThat(response.isRead()).isTrue();
        assertThat(sampleNotification.isRead()).isTrue();
    }

    @Test
    @DisplayName("Should mark all notifications as read in bulk")
    void testMarkAllAsRead() {
        when(notificationRepository.markAllAsReadForUser(10L)).thenReturn(4);

        int updated = notificationService.markAllAsRead(10L);

        assertThat(updated).isEqualTo(4);
    }

    @Test
    @DisplayName("Should broadcast announcement to all fans (userId = 0)")
    void testBroadcastAnnouncement() {
        when(notificationRepository.save(any(InAppNotification.class))).thenAnswer(inv -> {
            InAppNotification n = inv.getArgument(0);
            n.setId(99L);
            return n;
        });

        NotificationResponse response = notificationService.broadcastAnnouncement("Rain Update", "Gates open at 5 PM");

        assertThat(response.getUserId()).isEqualTo(0L);
        assertThat(response.getType()).isEqualTo(NotificationType.ANNOUNCEMENT);
    }
}

