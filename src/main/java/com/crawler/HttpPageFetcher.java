package com.crawler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Production implementation of {@link PageFetcher} using Java's built-in HttpClient.
 *
 * Behaviour:
 * - Follows redirects (HttpClient.Redirect.NORMAL)
 * - Skips non-HTML responses (checks Content-Type header)
 * - Returns empty on any error (timeout, connection failure, non-2xx status)
 * - Identifies itself with a custom User-Agent
 */
public class HttpPageFetcher implements PageFetcher {

    private final HttpClient client;
    private final CrawlConfig config;

    public HttpPageFetcher(CrawlConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(config.getRequestTimeout())
                .build();
    }

    @Override
    public Optional<String> fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(config.getRequestTimeout())
                    .header("User-Agent", "SimpleCrawler/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return Optional.empty();
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("");

            if (!contentType.contains("text/html")) {
                return Optional.empty();
            }

            return Optional.of(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
