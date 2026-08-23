package com.crawler;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Concurrent web crawler that visits all pages within a single subdomain.
 *
 * Concurrency model:
 *   A fixed-size thread pool processes pages in parallel. An AtomicInteger tracks
 *   the number of in-flight tasks. When a page is fetched and its links extracted,
 *   new tasks are submitted for unvisited same-subdomain links BEFORE the current
 *   task decrements the counter. This guarantees the counter only reaches zero when
 *   all work is truly complete — no race between parent decrement and child submit.
 *
 * Termination:
 *   A CountDownLatch signals the crawl() caller when pendingTasks reaches zero.
 *
 * Deduplication:
 *   A ConcurrentHashMap-backed Set with URL normalization ensures each page is
 *   visited at most once, even when multiple pages link to the same destination.
 *
 * This class is single-use: create a new Crawler instance for each crawl session.
 */
public class Crawler {

    private final PageFetcher fetcher;
    private final LinkExtractor linkExtractor;
    private final CrawlConfig config;
    private final Consumer<CrawlResult> resultConsumer;

    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final AtomicInteger pageCount = new AtomicInteger(0);
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    private ExecutorService executor;
    private String allowedHost;

    public Crawler(PageFetcher fetcher, LinkExtractor linkExtractor,
                   CrawlConfig config, Consumer<CrawlResult> resultConsumer) {
        this.fetcher = fetcher;
        this.linkExtractor = linkExtractor;
        this.config = config;
        this.resultConsumer = resultConsumer;
    }

    /**
     * Starts the crawl from the given seed URL and blocks until all reachable
     * same-subdomain pages have been visited (or the max-pages limit is reached).
     */
    public void crawl(String seedUrl) throws InterruptedException {
        String normalizedSeed = UrlUtils.normalize(seedUrl);
        this.allowedHost = UrlUtils.extractHost(normalizedSeed);
        this.executor = Executors.newFixedThreadPool(config.getMaxConcurrency());

        try {
            submitUrl(normalizedSeed);
            completionLatch.await();
        } finally {
            executor.shutdownNow();
        }
    }

    private void submitUrl(String url) {
        if (pageCount.get() >= config.getMaxPages()) {
            return;
        }
        if (!visited.add(url)) {
            return;
        }

        pendingTasks.incrementAndGet();
        executor.submit(() -> {
            try {
                processUrl(url);
            } finally {
                if (pendingTasks.decrementAndGet() == 0) {
                    completionLatch.countDown();
                }
            }
        });
    }

    private void processUrl(String url) {
        if (pageCount.incrementAndGet() > config.getMaxPages()) {
            return;
        }

        applyPolitenessDelay();

        var body = fetcher.fetch(url);
        if (body.isEmpty()) {
            resultConsumer.accept(new CrawlResult(url, List.of(), CrawlResult.Status.FETCH_ERROR));
            return;
        }

        List<String> links = linkExtractor.extract(body.get(), url);
        resultConsumer.accept(new CrawlResult(url, links, CrawlResult.Status.SUCCESS));

        for (String link : links) {
            if (UrlUtils.isSameSubdomain(link, allowedHost)) {
                submitUrl(link);
            }
        }
    }

    private void applyPolitenessDelay() {
        long delayMs = config.getPolitenessDelay().toMillis();
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
