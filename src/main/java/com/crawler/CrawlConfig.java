package com.crawler;

import java.time.Duration;

/**
 * Immutable configuration for a crawl session. Use the builder for readable construction.
 *
 * Trade-offs:
 * - maxPages is a soft limit: with concurrent workers, a few extra pages may be fetched
 *   before the limit is observed by all threads. This is acceptable for a crawler where
 *   approximate bounds are fine and strict enforcement would require costly synchronization.
 * - politenessDelay is per-request, not a global rate limit. With N threads this means up
 *   to N concurrent requests, each individually delayed. A shared rate limiter (e.g.
 *   Guava's RateLimiter) would give tighter control, but this keeps dependencies minimal.
 */
public final class CrawlConfig {

    private final int maxConcurrency;
    private final Duration requestTimeout;
    private final Duration politenessDelay;
    private final int maxPages;

    private CrawlConfig(Builder builder) {
        this.maxConcurrency = builder.maxConcurrency;
        this.requestTimeout = builder.requestTimeout;
        this.politenessDelay = builder.politenessDelay;
        this.maxPages = builder.maxPages;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public Duration getPolitenessDelay() {
        return politenessDelay;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxConcurrency = 4;
        private Duration requestTimeout = Duration.ofSeconds(10);
        private Duration politenessDelay = Duration.ofMillis(100);
        private int maxPages = 1000;

        private Builder() {}

        public Builder maxConcurrency(int maxConcurrency) {
            if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder politenessDelay(Duration politenessDelay) {
            this.politenessDelay = politenessDelay;
            return this;
        }

        public Builder maxPages(int maxPages) {
            if (maxPages < 1) throw new IllegalArgumentException("maxPages must be >= 1");
            this.maxPages = maxPages;
            return this;
        }

        public CrawlConfig build() {
            return new CrawlConfig(this);
        }
    }
}
