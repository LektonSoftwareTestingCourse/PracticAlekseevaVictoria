package com.processing.gateway.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCircuitBreakerTest {

    private static final Instant BASE = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void shouldAllowRequestsWhenCircuitIsClosed() {
        InMemoryCircuitBreaker breaker = newBreaker(BASE);
        assertThat(breaker.allowRequest("authorization")).isTrue();
    }

    @Test
    void shouldOpenAfterTwoFailures() {
        InMemoryCircuitBreaker breaker = newBreaker(BASE);
        breaker.recordFailure("authorization");
        assertThat(breaker.allowRequest("authorization")).isTrue();
        breaker.recordFailure("authorization");
        assertThat(breaker.allowRequest("authorization")).isFalse();
    }

    @Test
    void shouldAllowOnlyOneHalfOpenProbeAfterWait() {
        // Arrange
        MutableClock clock = new MutableClock(BASE);
        InMemoryCircuitBreaker breaker = InMemoryCircuitBreaker.forTesting(Duration.ofSeconds(10), 2, clock);
        breaker.recordFailure("authorization");
        breaker.recordFailure("authorization");
        assertThat(breaker.allowRequest("authorization")).isFalse();
        clock.advance(Duration.ofSeconds(11));

        // Act
        boolean first = breaker.allowRequest("authorization");
        boolean second = breaker.allowRequest("authorization");

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void shouldReopenAfterHalfOpenFailure() {
        MutableClock clock = new MutableClock(BASE);
        InMemoryCircuitBreaker breaker = InMemoryCircuitBreaker.forTesting(Duration.ofSeconds(10), 2, clock);
        breaker.recordFailure("authorization");
        breaker.recordFailure("authorization");
        clock.advance(Duration.ofSeconds(11));
        assertThat(breaker.allowRequest("authorization")).isTrue();
        breaker.recordFailure("authorization");
        assertThat(breaker.allowRequest("authorization")).isFalse();
    }

    @Test
    void shouldCloseCircuitAfterSuccess() {
        InMemoryCircuitBreaker breaker = newBreaker(BASE);
        breaker.recordFailure("authorization");
        breaker.recordFailure("authorization");
        breaker.recordSuccess("authorization");
        assertThat(breaker.allowRequest("authorization")).isTrue();
    }

    @Test
    void shouldIsolateStateByService() {
        InMemoryCircuitBreaker breaker = newBreaker(BASE);
        breaker.recordFailure("authorization");
        breaker.recordFailure("authorization");
        assertThat(breaker.allowRequest("logger")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            current = initial;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private InMemoryCircuitBreaker newBreaker(Instant timestamp) {
        return InMemoryCircuitBreaker.forTesting(Duration.ofSeconds(10), 2, Clock.fixed(timestamp, ZoneOffset.UTC));
    }
}
