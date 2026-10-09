package com.edocs.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientInfo {

    private ClientInfo() {
    }

    // Caller IP for audit entries; forwarded headers are resolved by server.forward-headers-strategy.
    public static String ip() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return "Internal";
    }
}
