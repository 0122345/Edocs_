package com.edocs.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Publishes to RabbitMQ after the surrounding database transaction commits, so a rollback never emits messages.
@Component
public class MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisher.class);

    private final RabbitTemplate rabbit;

    public MessagePublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void notification(NotificationMessage message, Runnable onFailure) {
        afterCommit(() -> send(RabbitConfig.NOTIFICATIONS_EXCHANGE, "notify." + message.channel(), message, onFailure));
    }

    public void event(DomainEvent event) {
        afterCommit(() -> send(RabbitConfig.EVENTS_EXCHANGE, event.type(), event, null));
    }

    private void send(String exchange, String routingKey, Object payload, Runnable onFailure) {
        try {
            rabbit.convertAndSend(exchange, routingKey, payload);
        } catch (AmqpException ex) {
            log.warn("RabbitMQ publish to {}/{} failed: {}", exchange, routingKey, ex.getMessage());
            if (onFailure != null) {
                onFailure.run();
            }
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
