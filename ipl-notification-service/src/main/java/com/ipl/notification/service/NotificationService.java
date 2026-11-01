package com.ipl.notification.service;

import com.ipl.notification.dto.response.NotificationResponse;
import com.ipl.notification.entity.InAppNotification;
import com.ipl.notification.enums.NotificationType;
import com.ipl.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(Long userId) {
        return notificationRepository.findForUser(userId)
                .stream()
                .map(NotificationResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadForUser(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long id, Long userId) {
        InAppNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));

        notification.setRead(true);
        InAppNotification saved = notificationRepository.save(notification);
        return NotificationResponse.fromEntity(saved);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadForUser(userId);
    }

    @Transactional
    public NotificationResponse sendNotification(Long userId, String title, String message,
                                                 NotificationType type, String refType, String refId) {
        InAppNotification notification = InAppNotification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .referenceType(refType)
                .referenceId(refId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        InAppNotification saved = notificationRepository.save(notification);
        log.info("Saved in-app notification id={} for user={}", saved.getId(), userId);
        return NotificationResponse.fromEntity(saved);
    }

    @Transactional
    public NotificationResponse broadcastAnnouncement(String title, String message) {
        return sendNotification(0L, title, message, NotificationType.ANNOUNCEMENT, "SYSTEM", null);
    }
}

