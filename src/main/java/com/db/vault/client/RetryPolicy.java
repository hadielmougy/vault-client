package com.db.vault.client;

import java.time.Duration;

/**
 * Retries for connection failures and 429, 500, 502, 503 and 504 answers, with
 * exponential backoff. Safe for writes too: creates and proposals carry an
 * idempotency key, so a retried request is never applied twice. Business
 * rejections (400, 403, 409) are never retried.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(4, Duration.ofMillis(200), Duration.ofSeconds(5));
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO);
    }

    Duration backoff(int attempt) {
        long millis = initialBackoff.toMillis() * (1L << Math.min(attempt - 1, 16));
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }
}
