package com.sage.backend.common;

import java.util.UUID;

public final class SessionIdGenerator {
    private SessionIdGenerator() {
    }

    public static String generate() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "");
    }
}
