package com.gobang.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestRateLimiterTest {

    @Test
    void rejectsOnlyTheKeyThatExceedsItsWindow() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        limiter.enforce("login:one", 2, Duration.ofMinutes(1));
        limiter.enforce("login:one", 2, Duration.ofMinutes(1));

        assertThatThrownBy(() -> limiter.enforce("login:one", 2, Duration.ofMinutes(1)))
                .isInstanceOf(RequestRateLimiter.RateLimitExceededException.class);
        assertThatCode(() -> limiter.enforce("login:two", 2, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }
}
