package com.edocs.messaging;

// Payload on edocs.notifications: one email or SMS to deliver.
public record NotificationMessage(String notificationId, String channel, String to, String subject, String body) {
}
