package com.edocs.notification;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("notifications")
@CompoundIndex(name = "ix_recipient_created", def = "{'recipientId': 1, 'createdAt': -1}")
public class Notification {

    public enum DeliveryStatus { QUEUED, SENT, FAILED, NOT_REQUIRED }

    @Id
    private String id;
    private String orgId;
    private String recipientId;
    private String recipientEmail;
    private String recipientPhone;
    private NotificationType type;
    private Channel channel;
    private String title;
    private String body;
    private String link;
    private Instant createdAt;
    private boolean read;
    private DeliveryStatus deliveryStatus;
    private Instant sentAt;
}
