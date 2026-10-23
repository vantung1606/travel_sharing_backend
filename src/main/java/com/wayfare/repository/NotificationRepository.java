package com.wayfare.repository;

import com.wayfare.entity.Notification;
import com.wayfare.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);

    List<Notification> findByRecipientAndIsReadFalseOrderByCreatedAtDesc(User recipient);

    long countByRecipientAndIsReadFalse(User recipient);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient = :recipient")
    void markAllAsReadByRecipient(@Param("recipient") User recipient);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.targetUrl LIKE '%/admin/reports%' " +
           "AND n.recipient.id NOT IN (SELECT u.id FROM User u JOIN u.roles r WHERE r.name = 'ROLE_ADMIN') " +
           "AND n.recipient.email <> 'admin@gmail.com'")
    int deleteSpuriousAdminNotifications();
}
