package com.edocs.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.edocs.messaging.MessagePublisher;
import com.edocs.messaging.NotificationMessage;
import com.edocs.notification.Notification.DeliveryStatus;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final MongoTemplate mongo;
    private final MessagePublisher publisher;

    public NotificationService(NotificationRepository repo, MongoTemplate mongo, MessagePublisher publisher) {
        this.repo = repo;
        this.mongo = mongo;
        this.publisher = publisher;
    }

    public record Recipient(UUID userId, String email, String phone) {

        public static Recipient user(UUID userId, String email) {
            return new Recipient(userId, email, null);
        }

        public static Recipient external(String email) {
            return new Recipient(null, email, null);
        }
    }

    public Notification send(UUID orgId, Recipient to, NotificationType type, Channel channel, String title, String body, String link) {
        return send(orgId, to, type, channel, title, body, link, body);
    }

    // Stores the bell copy and queues email/SMS via RabbitMQ; secrets such as OTPs go only in `wireBody`, never in the stored copy.
    public Notification send(UUID orgId, Recipient to, NotificationType type, Channel channel, String title, String body, String link, String wireBody) {
        boolean external = channel != Channel.IN_APP;
        Notification n = repo.save(Notification.builder()
                .orgId(orgId.toString())
                .recipientId(to.userId() == null ? null : to.userId().toString())
                .recipientEmail(to.email())
                .recipientPhone(to.phone())
                .type(type).channel(channel).title(title).body(body).link(link)
                .createdAt(Instant.now())
                .deliveryStatus(external ? DeliveryStatus.QUEUED : DeliveryStatus.NOT_REQUIRED)
                .build());
        if (external) {
            String address = channel == Channel.SMS && to.phone() != null ? to.phone() : to.email();
            String wire = channel == Channel.SMS && to.phone() != null ? "sms" : "email";
            publisher.notification(new NotificationMessage(n.getId(), wire, address, title, wireBody),
                    () -> markDelivery(n.getId(), DeliveryStatus.FAILED));
        }
        return n;
    }

    public List<Notification> listFor(UUID userId) {
        return repo.findByRecipientIdOrderByCreatedAtDesc(userId.toString(), PageRequest.of(0, 50));
    }

    public void markRead(UUID userId, List<String> ids) {
        Criteria c = Criteria.where("recipientId").is(userId.toString()).and("read").is(false);
        if (ids != null) {
            c = c.and("_id").in(ids);
        }
        mongo.updateMulti(new Query(c), new Update().set("read", true), Notification.class);
    }

    public void markDelivery(String notificationId, DeliveryStatus status) {
        Update update = new Update().set("deliveryStatus", status);
        if (status == DeliveryStatus.SENT) {
            update.set("sentAt", Instant.now());
        }
        mongo.updateFirst(new Query(Criteria.where("_id").is(notificationId)), update, Notification.class);
    }
}
