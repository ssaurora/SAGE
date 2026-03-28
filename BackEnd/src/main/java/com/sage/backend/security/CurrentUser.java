package com.sage.backend.security;

public record CurrentUser(Long userId, String username, String role) {
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
