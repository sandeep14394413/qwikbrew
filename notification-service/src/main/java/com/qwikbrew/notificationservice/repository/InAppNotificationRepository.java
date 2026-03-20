package com.qwikbrew.notificationservice.repository;

import com.qwikbrew.notificationservice.model.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, String> {

    List<InAppNotification> findByUserIdOrderByCreatedAtDesc(String userId);

    List<InAppNotification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId);

    long countByUserIdAndIsReadFalse(String userId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllReadForUser(String userId);
}
