package com.sage.backend.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class TaskIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private TaskIdGenerator() {
    }

    public static String generate() {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "task_" + timestamp + "_" + randomPart;
    }
}

