# Web Crawler — Design Document

## Architecture Overview

The crawler is structured around five collaborating components, each with a single responsibility:

```
App (CLI)
 │
 └─► Crawler (orchestration + concurrency)
      ├─► PageFetcher (HTTP I/O)
      ├─► LinkExtractor (HTML parsing)
      └─► UrlUtils (normalization + subdomain filtering)
```

| Component | Responsibility |
|---|---|
| `Crawler` | Manages the work queue, thread pool, visited set, and termination detection. |
| `PageFetcher` | Interface for fetching a URL's HTML body. `HttpPageFetcher` is the production implementation. |
| `LinkExtractor` | Parses raw HTML with Jsoup and returns a deduplicated list of normalized absolute URLs. |
| `UrlUtils` | Stateless utilities for URL normalization and strict subdomain comparison. |
| `CrawlConfig` | Immutable, builder-constructed configuration (concurrency, timeouts, limits). |
| `CrawlResult` | Value object (Java record) carrying a visited URL, its discovered links, and a success/error status. |

## Design Decisions and Trade-offs

### 1. Interface-based HTTP abstraction (`PageFetcher`)

The `Crawler` depends on a `PageFetcher` interface, not directly on `HttpClient`. This is the most important structural decision in the project because it makes the entire crawl pipeline testable without the network. `CrawlerTest` injects a `FakePageFetcher` that maps URLs to canned HTML strings, letting us verify orchestration logic — subdomain filtering, deduplication, error handling, max-pages enforcement — in milliseconds with deterministic results.

**Trade-off:** An extra layer of indirection for something that only has one production implementation. In this case the cost is near zero (one small interface file) and the testing benefit is substantial.

### 2. Decoupled output via `Consumer<CrawlResult>`

The `Crawler` does not print anything. It accepts a `Consumer<CrawlResult>` at construction and invokes it with each result. The CLI (`App`) passes a synchronized print method; tests pass `results::add` to collect results in a list.

**Why this matters:** It separates what to do with results from how to produce them. If tomorrow the results need to be written to a file, streamed to a queue, or aggregated into a sitemap, nothing inside `Crawler` changes.

### 3. CrawlResult reports all links, not just followed ones

The `CrawlResult.links()` list includes every HTTP/HTTPS link found on the page — internal and external. The subdomain filter is applied by the `Crawler` when deciding which links to *follow*, not by the `LinkExtractor` when deciding which links to *report*.

**Why:** The requirement asks to print "a list of links found on that page." Filtering at the extraction layer would hide information. Keeping extraction and filtering as separate concerns also makes each independently testable.

### 4. URL normalization strategy

`UrlUtils.normalize()` applies a deliberate set of transformations:

| Transformation | Rationale |
|---|---|
| Lowercase scheme and host | RFC 3986: scheme and host are case-insensitive |
| Remove default ports (80/443) | `http://x.com:80/` and `http://x.com/` are the same resource |
| Strip fragment (`#...`) | Fragments are client-side anchors, not distinct server resources |
| Remove trailing slash (except root) | Most servers treat `/page` and `/page/` as the same resource |
| Preserve query strings | `?page=1` and `?page=2` are typically different resources |

**Trade-off:** Trailing slash removal is a heuristic. Some servers treat `/page` and `/page/` as different resources (returning different content or a redirect). For the vast majority of websites this normalization prevents duplicate crawling, and the alternative (treating them as different) leads to more wasted requests than the rare false deduplication.

### 5. Strict subdomain matching

`UrlUtils.isSameSubdomain()` requires an exact host match. Starting from `crawlme.conco.com`, the crawler will **not** follow links to `conco.com`, `www.crawlme.conco.com`, or `community.conco.com`.

**Why strict:** The requirement says "limited to one subdomain." Relaxing this (e.g., matching any subdomain of `conco.com`) would be a different feature with different risk profiles — it could inadvertently spider an entire organisation's web presence.

### 6. No Spring Boot

The exercise offered Spring Boot as an option. I chose plain Java because:

- A CLI crawler has no use for dependency injection containers, web servers, or auto-configuration.
- Constructor injection gives us all the DI we need (the `Crawler` constructor accepts its collaborators).
- Fewer dependencies means a faster build, smaller artifact, and less surface area to understand.

Spring Boot would be warranted if this were a long-running service with an API for submitting crawl jobs, monitoring progress, etc.

## Concurrency Model

### Thread pool + atomic counter

The `Crawler` uses a fixed-size `ExecutorService` (defaulting to 4 threads). The core concurrency mechanism is an `AtomicInteger pendingTasks` combined with a `CountDownLatch`:

```
submitUrl(url):
    if already visited → return
    visited.add(url)
    pendingTasks.increment()
    executor.submit(() → {
        try {
            processUrl(url)              // fetch, extract, report
            for each discovered link:
                submitUrl(link)          // may increment pendingTasks
        } finally {
            if pendingTasks.decrement() == 0:
                completionLatch.countDown()
        }
    })
```

**Termination correctness:** The critical invariant is that child URLs are submitted (incrementing the counter) *before* the parent task decrements it. This means the counter can only reach zero when every task has finished processing and all of its discovered links have either been submitted or skipped (already visited). There is no window where the counter is zero but work remains.

**Deduplication under concurrency:** The `visited` set is backed by `ConcurrentHashMap.newKeySet()`. The `visited.add(url)` call is atomic — if two threads discover the same URL simultaneously, only one will get `true` from `add()` and proceed to submit a task.

### Known limitation: per-thread politeness delay

The `politenessDelay` is applied via `Thread.sleep()` inside each worker thread. With N concurrent threads, this means up to N requests can be in-flight simultaneously, each individually delayed. This is not a true rate limiter — it does not guarantee a maximum request rate across all threads.

**Why this was acceptable:** A proper rate limiter (e.g., token bucket) would add either a dependency (Guava's `RateLimiter`) or significant custom code. The per-thread delay provides basic politeness — no thread fires requests in a tight loop — and is transparent in its behaviour.

## Testing Strategy

The test suite validates three layers independently:

| Test class | What it validates | Technique |
|---|---|---|
| `UrlUtilsTest` | Normalization rules, subdomain matching, host extraction | Pure unit tests against static methods |
| `LinkExtractorTest` | HTML parsing: relative URLs, absolute URLs, non-HTTP filtering, deduplication, edge cases | Unit tests with inline HTML snippets |
| `CrawlerTest` | Orchestration: subdomain boundary, cycle detection, error handling, max-pages, result completeness | Integration tests with `FakePageFetcher` simulating a small website graph |

**Key property of the test suite:** No test requires network access. The `FakePageFetcher` maps URLs to HTML strings, so `CrawlerTest` exercises the full crawl algorithm — concurrent workers, visited set, subdomain filtering — against a deterministic in-memory "website." Tests run in milliseconds and are not flaky.

## Potential Improvements

### 1. Robots.txt compliance

The crawler currently ignores `robots.txt`. A production crawler should fetch and parse `/robots.txt` before crawling, respecting `Disallow` rules and `Crawl-delay` directives. This could be implemented as a `RobotsTxtFilter` that the `Crawler` consults before submitting a URL.

### 2. Global rate limiter

Replace the per-thread `Thread.sleep()` with a shared rate limiter (e.g., a token-bucket implementation or Guava's `RateLimiter`). This would guarantee a configurable maximum requests-per-second across all threads, which is more polite and more predictable.

### 3. Crawl depth limit

Add a configurable maximum depth (hops from the seed URL). The current implementation limits by total page count (`maxPages`), but not by depth. A deeply linked site could exhaust the page budget before discovering breadth. Depth tracking would require passing a depth counter alongside each URL through the work queue.

### 4. Retry with backoff

`HttpPageFetcher` currently returns `Optional.empty()` on any failure. Transient errors (503, timeouts, connection resets) could benefit from a bounded retry with exponential backoff. This should be configurable and capped (e.g., 3 retries) to avoid getting stuck on permanently broken pages.

### 5. Redirect-aware subdomain filtering

When `HttpClient` follows a redirect (e.g., `http://site.com` → `https://www.site.com`), the final URL may be on a different subdomain. The current design filters based on the pre-redirect URL. An improvement would be to check the final resolved URL against the subdomain filter and to add the final URL to the visited set to prevent re-crawling it.

### 6. Bounded work queue

The `ExecutorService` uses an unbounded `LinkedBlockingQueue` by default. On a very large site, this could accumulate millions of pending tasks in memory. Switching to a `ThreadPoolExecutor` with a bounded `ArrayBlockingQueue` and a caller-runs rejection policy would apply natural backpressure — when the queue is full, the submitting thread processes the task itself, slowing down discovery.

### 7. Structured logging

The current implementation uses `System.out`/`System.err`. A structured logging framework (SLF4J + Logback) would enable log levels, per-component filtering, and structured output (JSON) for ingestion into monitoring systems. Particularly useful for diagnosing crawl behaviour at scale.

### 8. Persistent crawl state

For very large crawls, the in-memory visited set and work queue could be backed by an embedded database (e.g., SQLite, RocksDB). This enables pause/resume, crash recovery, and crawling sites that exceed available memory.

### 9. Content-type pre-check via HEAD request

Before fetching the full body, issue an HTTP `HEAD` request to check the `Content-Type` header. This avoids downloading large binary files (PDFs, images, videos) only to discard them because they are not HTML. The trade-off is an extra round-trip per page, but it saves bandwidth on sites with many non-HTML resources.

### 10. Crawl metrics and reporting

Expose crawl statistics — pages fetched, errors encountered, pages/second, queue depth, elapsed time — either as periodic console output or via a simple metrics interface. This gives visibility into crawl progress and helps diagnose performance issues.
