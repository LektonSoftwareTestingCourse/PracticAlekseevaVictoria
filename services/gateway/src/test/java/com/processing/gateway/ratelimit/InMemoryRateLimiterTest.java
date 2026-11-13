package com.processing.gateway.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    @Test
    void shouldRejectRequestWhenBucketHasNoTokens() {
        // Arrange
        AtomicLong nanos = new AtomicLong(0);
        InMemoryRateLimiter limiter = limiter(2, 1.0, nanos::get);

        // Act
        boolean first = limiter.allowRequest("client-1");
        boolean second = limiter.allowRequest("client-1");
        boolean third = limiter.allowRequest("client-1");

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isFalse();
    }

    @Test
    void shouldRefillTokenAfterOneSecond() {
        // Arrange
        AtomicLong nanos = new AtomicLong(0);
        InMemoryRateLimiter limiter = limiter(1, 1.0, nanos::get);
        assertThat(limiter.allowRequest("client-1")).isTrue();
        assertThat(limiter.allowRequest("client-1")).isFalse();

        // Act
        nanos.addAndGet(1_000_000_000L);

        // Assert
        assertThat(limiter.allowRequest("client-1")).isTrue();
    }

    @Test
    void shouldMaintainSeparateBucketsForDifferentClients() {
        // Arrange
        AtomicLong nanos = new AtomicLong(0);
        InMemoryRateLimiter limiter = limiter(1, 0, nanos::get);

        // Act+Assert
        assertThat(limiter.allowRequest("client-1")).isTrue();
        assertThat(limiter.allowRequest("client-1")).isFalse();
        assertThat(limiter.allowRequest("client-2")).isTrue();
    }

    @Test
    void shouldRejectAllRequestsWithZeroCapacity() {
        InMemoryRateLimiter limiter = limiter(0, 1, () -> 0L);
        assertThat(limiter.allowRequest("client-1")).isFalse();
    }

    private InMemoryRateLimiter limiter(int capacity, double rate, Ticker ticker) {
        return InMemoryRateLimiter.forTesting(capacity, rate, Duration.ofMinutes(10), 100, ticker);
    }
}