package com.edocs.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.edocs.common.ApiException;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw ApiException.unauthorized("Your session has ended. Sign in again.");
    }

    public static void require(Permission permission) {
        if (!get().can(permission)) {
            throw ApiException.forbidden("Your role does not allow this action.");
        }
    }
}
