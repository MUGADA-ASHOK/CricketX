package com.ipl.notification.repository;

import com.ipl.notification.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<InAppNotification, Long> {

    @Query("SELECT n FROM InAppNotification n WHERE n.userId = :userId OR n.userId IS NULL OR n.userId = 0 ORDER BY n.createdAt DESC")
    List<InAppNotification> findForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(n) FROM InAppNotification n WHERE (n.userId = :userId OR n.userId IS NULL OR n.userId = 0) AND n.isRead = false")
    long countUnreadForUser(@Param("userId") Long userId);

    Optional<InAppNotification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.isRead = true WHERE (n.userId = :userId OR n.userId IS NULL OR n.userId = 0) AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") Long userId);
}

