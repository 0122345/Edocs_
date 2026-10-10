package com.edocs.config;

import java.util.Arrays;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// Refuses to start outside the dev profile with missing or published keys, so a forgotten env var can't make tokens forgeable.
@Component
public class SecretsGuard {

    static final String PUBLISHED_MASTER_KEY = "3q2+7wABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGho=";

    public SecretsGuard(EdocsProperties props, Environment env) {
        if (Arrays.asList(env.getActiveProfiles()).contains("dev")) {
            return;
        }
        String jwt = props.security().jwtSecret();
        if (placeholder(jwt)) {
            throw new IllegalStateException("Set JWT_SECRET to a random value of at least 32 bytes (e.g. openssl rand -base64 48), or run with the dev profile.");
        }
        String master = props.crypto().masterKey();
        if (placeholder(master) || master.equals(PUBLISHED_MASTER_KEY)) {
            throw new IllegalStateException("Set MASTER_KEY to a base64 256-bit key (e.g. openssl rand -base64 32), or run with the dev profile.");
        }
    }

    private static boolean placeholder(String value) {
        return value == null || value.isBlank() || value.contains("change-me");
    }
}
