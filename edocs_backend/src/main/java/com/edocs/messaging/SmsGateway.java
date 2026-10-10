package com.edocs.messaging;

// Port for an SMS provider (e.g. Twilio, Africa's Talking); swap the bean to go live.
public interface SmsGateway {

    void send(String to, String body);

    // False for stand-ins that never reach a phone, so codes go by email instead.
    default boolean delivers() {
        return true;
    }
}
