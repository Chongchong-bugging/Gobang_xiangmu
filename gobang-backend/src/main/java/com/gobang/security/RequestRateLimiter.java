package com.gobang.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimiter {

    private static final int MAX_KEYS = 10_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void enforce(String key, int limit, Duration period) {
        long now = System.nanoTime();
        long periodNanos = period.toNanos();
        if (windows.size() >= MAX_KEYS) {
            windows.entrySet().removeIf(entry ->
                    now - entry.getValue().startedAt >= entry.getValue().periodNanos);
            if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
                throw new RateLimitExceededException();
            }
        }
        Window current = windows.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous.startedAt >= periodNanos) {
                return new Window(now, 1, periodNanos);
            }
            return new Window(previous.startedAt, previous.count + 1, periodNanos);
        });
        if (current.count > limit) throw new RateLimitExceededException();
    }

    private record Window(long startedAt, int count, long periodNanos) {}

    public static final class RateLimitExceededException extends RuntimeException {}
}
