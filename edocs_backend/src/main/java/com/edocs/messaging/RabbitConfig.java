package com.edocs.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Topology: notifications (email/SMS) and domain events, each queue dead-lettered to edocs.dlq after retries.
@Configuration
public class RabbitConfig {

    public static final String NOTIFICATIONS_EXCHANGE = "edocs.notifications";
    public static final String EVENTS_EXCHANGE = "edocs.events";
    public static final String DEAD_LETTER_EXCHANGE = "edocs.dlx";

    public static final String EMAIL_QUEUE = "edocs.notifications.email";
    public static final String SMS_QUEUE = "edocs.notifications.sms";
    public static final String DOCUMENT_EVENTS_QUEUE = "edocs.events.document";
    public static final String DEAD_LETTER_QUEUE = "edocs.dlq";

    // RabbitMQ-specific; the embedded AMQP broker used in Docker-free tests does not support it.
    private final boolean deadLettering;

    public RabbitConfig(@Value("${edocs.messaging.dead-lettering:true}") boolean deadLettering) {
        this.deadLettering = deadLettering;
    }

    @Bean
    TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue emailQueue() {
        return durableWithDlq(EMAIL_QUEUE);
    }

    @Bean
    Queue smsQueue() {
        return durableWithDlq(SMS_QUEUE);
    }

    @Bean
    Queue documentEventsQueue() {
        return durableWithDlq(DOCUMENT_EVENTS_QUEUE);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(notificationsExchange()).with("notify.email");
    }

    @Bean
    Binding smsBinding() {
        return BindingBuilder.bind(smsQueue()).to(notificationsExchange()).with("notify.sms");
    }

    @Bean
    Binding documentEventsBinding() {
        return BindingBuilder.bind(documentEventsQueue()).to(eventsExchange()).with("document.#");
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_QUEUE);
    }

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("com.edocs.*");
    }

    private Queue durableWithDlq(String name) {
        QueueBuilder builder = QueueBuilder.durable(name);
        if (deadLettering) {
            builder.withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE);
        }
        return builder.build();
    }
}
