package com.edocs.security;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.edocs.common.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

// Fixed-window limiter for unauthenticated auth endpoints: 20 attempts per minute per client IP.
@Component
public class LoginRateLimiter {

    private static final int LIMIT = 20;

    private final Cache<String, AtomicInteger> windows = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();

    public void check(String ip) {
        int count = windows.get(ip, k -> new AtomicInteger()).incrementAndGet();
        if (count > LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Wait a minute and try again.");
        }
    }
}
