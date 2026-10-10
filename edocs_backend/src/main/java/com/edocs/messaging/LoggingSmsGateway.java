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
        // Codes and phone digits are masked so OTPs never reach the logs.
        log.info("SMS to {}: {}", mask(to), body == null ? "" : body.replaceAll("\\d{4,}", "••••"));
    }

    @Override
    public boolean delivers() {
        return false;
    }

    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "••••" : "••••" + phone.substring(phone.length() - 2);
    }
}
