package com.crawler;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Crawler's orchestration logic using a fake PageFetcher that
 * returns canned HTML. This validates crawl behaviour (subdomain filtering,
 * deduplication, error handling) without any network calls.
 */
class CrawlerTest {

    /**
     * Simulates a small website in memory. Maps URLs to HTML content.
     */
    private static class FakePageFetcher implements PageFetcher {
        private final Map<String, String> pages = new HashMap<>();

        void addPage(String url, String html) {
            pages.put(UrlUtils.normalize(url), html);
        }

        @Override
        public Optional<String> fetch(String url) {
            return Optional.ofNullable(pages.get(UrlUtils.normalize(url)));
        }
    }

    private CrawlConfig fastConfig() {
        return CrawlConfig.builder()
                .maxConcurrency(2)
                .politenessDelay(Duration.ZERO)
                .build();
    }

    @Test
    void crawlsAllReachableSameSubdomainPages() throws InterruptedException {
        FakePageFetcher fetcher = new FakePageFetcher();
        fetcher.addPage("https://site.com/", """
                <html><body>
                <a href="https://site.com/about">About</a>
                <a href="https://site.com/contact">Contact</a>
                </body></html>
                """);
        fetcher.addPage("https://site.com/about", """
                <html><body>
                <a href="https://site.com/">Home</a>
                </body></html>
                """);
        fetcher.addPage("https://site.com/contact", """
                <html><body>
                <a href="https://site.com/">Home</a>
                </body></html>
                """);

        List<CrawlResult> results = new CopyOnWriteArrayList<>();
        Crawler crawler = new Crawler(fetcher, new LinkExtractor(), fastConfig(), results::add);
        crawler.crawl("https://site.com/");

        Set<String> visitedUrls = new HashSet<>();
        for (CrawlResult r : results) {
            visitedUrls.add(r.url());
        }

        assertThat(visitedUrls).containsExactlyInAnyOrder(
                "https://site.com/",
                "https://site.com/about",
                "https://site.com/contact"
        );
    }

    @Test
    void doesNotFollowExternalLinks() throws InterruptedException {
        FakePageFetcher fetcher = new FakePageFetcher();
        fetcher.addPage("https://mysite.com/", """
                <html><body>
                <a href="https://mysite.com/page">Internal</a>
                <a href="https://external.com/page">External</a>
                <a href="https://other.mysite.com/page">Other Subdomain</a>
                </body></html>
                """);
        fetcher.addPage("https://mysite.com/page", "<html><body></body></html>");
        // External pages exist but should not be visited
        fetcher.addPage("https://external.com/page", "<html><body></body></html>");
        fetcher.addPage("https://other.mysite.com/page", "<html><body></body></html>");

        List<CrawlResult> results = new CopyOnWriteArrayList<>();
        Crawler crawler = new Crawler(fetcher, new LinkExtractor(), fastConfig(), results::add);
        crawler.crawl("https://mysite.com/");

        Set<String> visitedUrls = new HashSet<>();
        for (CrawlResult r : results) {
            visitedUrls.add(r.url());
        }

        assertThat(visitedUrls).containsExactlyInAnyOrder(
                "https://mysite.com/",
                "https://mysite.com/page"
        );
    }

    @Test
    void reportsAllLinksIncludingExternal() throws InterruptedException {
        FakePageFetcher fetcher = new FakePageFetcher();
        fetcher.addPage("https://mysite.com/", """
                <html><body>
                <a href="https://mysite.com/about">About</a>
                <a href="https://facebook.com/mysite">Facebook</a>
                </body></html>
                """);
        fetcher.addPage("https://mysite.com/about", "<html><body></body></html>");

        List<CrawlResult> results = new CopyOnWriteArrayList<>();
        Crawler crawler = new Crawler(fetcher, new LinkExtractor(), fastConfig(), results::add);
        crawler.crawl("https://mysite.com/");

        CrawlResult rootResult = results.stream()
                .filter(r -> r.url().equals("https://mysite.com/"))
                .findFirst()
                .orElseThrow();

        // All links should be reported, including external ones
        assertThat(rootResult.links()).containsExactlyInAnyOrder(
                "https://mysite.com/about",
                "https://facebook.com/mysite"
        );
    }

    @Test
    void doesNotRevisitAlreadyVisitedPages() throws InterruptedException {
        FakePageFetcher fetcher = new FakePageFetcher();
        fetcher.addPage("https://site.com/", """
                <html><body>
                <a href="https://site.com/a">A</a>
                <a href="https://site.com/b">B</a>
                </body></html>
                """);
        fetcher.addPage("https://site.com/a", """
                <html><body>
                <a href="https://site.com/b">B again</a>
                <a href="https://site.com/">Home</a>
                </body></html>
                """);
        fetcher.addPage("https://site.com/b", """
                <html><body>
                <a href="https://site.com/a">A again</a>
                <a href="https://site.com/">Home</a>
                </body></html>
                """);

        List<CrawlResult> results = new CopyOnWriteArrayList<>();
        Crawler crawler = new Crawler(fetcher, new LinkExtractor(), fastConfig(), results::add);
        crawler.crawl("https://site.com/");

        // Each URL should appear exactly once in results
        List<String> visitedUrls = results.stream().map(CrawlResult::url).toList();
        assertThat(visitedUrls).hasSize(3);
        assertThat(new HashSet<>(visitedUrls)).hasSize(3);
    }

    @Test
    void handlesFetchErrors() throws InterruptedException {
        FakePageFetcher fetcher = new FakePageFetcher();
        fetcher.addPage("https://site.com/", """
                <html><body>
                <a href="https://site.com/missing">Missing</a>
                </body></html>
                """);
        // /missing is NOT added to fetcher, simulating a 404/error

        List<CrawlResult> results = new CopyOnWriteArrayList<>();
        Crawler crawler = new Crawler(fetcher, new LinkExtractor(), fastConfig(), results::add);
        crawler.crawl("https://site.com/");

        CrawlResult errorResult = results.stream()
                .filter(r -> r.url().equals("https://site.com/missing"))
                .findFirst()
                .orElseThrow();

        assertThat(errorResult.status()).isEqualTo(CrawlResult.Status.FETCH_ERROR);
        assertThat(errorResult.links()).isEmpty();
    }

    @Test
    void respectsMaxPagesLimit() throws InterruptedException {
        FakePageFetcher fetcher = new FakePageFetcher();
        // Create a chain of 10 pages
        for (int i = 0; i < 10; i++) {
            String url = "https://site.com/page" + i;
            String nextLink = "https://site.com/page" + (i + 1);
            fetcher.addPage(url, String.format(
                    "<html><body><a href=\"%s\">Next</a></body></html>", nextLink));
        }

        CrawlConfig config = CrawlConfig.builder()
                .maxConcurrency(1)
                .maxPages(3)
                .politenessDelay(Duration.ZERO)
                .build();

        List<CrawlResult> results = new CopyOnWriteArrayList<>();
        Crawler crawler = new Crawler(fetcher, new LinkExtractor(), config, results::add);
        crawler.crawl("https://site.com/page0");

        // Should not exceed maxPages (soft limit, so allow a small margin)
        assertThat(results.size()).isLessThanOrEqualTo(4);
    }
}
