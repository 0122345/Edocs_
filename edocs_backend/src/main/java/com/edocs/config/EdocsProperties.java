package com.edocs.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("edocs")
public record EdocsProperties(
        String frontendUrl,
        List<String> corsOrigins,
        Security security,
        Crypto crypto,
        Otp otp,
        Seed seed,
        Mail mail) {

    public record Security(String jwtSecret, Duration tokenTtl, int maxFailedLogins, Duration lockout, String demoMfaCode) {
    }

    public record Crypto(String masterKey) {
    }

    public record Otp(Duration ttl, int maxAttempts, boolean echoInApp) {
    }

    public record Seed(boolean enabled, String demoPassword) {
    }

    public record Mail(String from) {
    }
}
