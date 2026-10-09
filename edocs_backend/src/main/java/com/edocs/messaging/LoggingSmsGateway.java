package com.edocs.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Development SMS provider: writes the message to the log instead of a carrier.
@Component
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    @Override
    public void send(String to, String body) {
        log.info("SMS to {}: {}", to, body);
    }
}
