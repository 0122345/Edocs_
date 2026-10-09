package com.edocs.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.edocs.config.EdocsProperties;
import com.edocs.notification.Notification.DeliveryStatus;
import com.edocs.notification.NotificationService;

// Consumers for edocs.notifications.*; failures are retried (3x, backoff) and then dead-lettered.
@Component
public class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final JavaMailSender mail;
    private final SmsGateway sms;
    private final NotificationService notifications;
    private final EdocsProperties props;

    public NotificationListeners(JavaMailSender mail, SmsGateway sms, NotificationService notifications, EdocsProperties props) {
        this.mail = mail;
        this.sms = sms;
        this.notifications = notifications;
        this.props = props;
    }

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void onEmail(NotificationMessage message) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(props.mail().from());
        email.setTo(message.to());
        email.setSubject("[Edocs] " + message.subject());
        email.setText(message.body());
        mail.send(email);
        notifications.markDelivery(message.notificationId(), DeliveryStatus.SENT);
        log.debug("Email delivered to {}", message.to());
    }

    @RabbitListener(queues = RabbitConfig.SMS_QUEUE)
    public void onSms(NotificationMessage message) {
        sms.send(message.to(), message.body());
        notifications.markDelivery(message.notificationId(), DeliveryStatus.SENT);
    }
}
