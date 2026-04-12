package com.sage.backend.common;

import java.util.UUID;

public final class SessionMessageIdGenerator {
    private SessionMessageIdGenerator() {
    }

    public static String generate() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }
}
