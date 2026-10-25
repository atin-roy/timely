package com.atinroy.timely.auth;

import org.springframework.security.core.context.SecurityContextHolder;

// Who is calling, and which login token they used.
public record AuthPrincipal(Integer userId, byte[] tokenHash) {
    public static AuthPrincipal current() {
        return (AuthPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public static Integer currentUserId() {
        return current().userId();
    }
}
