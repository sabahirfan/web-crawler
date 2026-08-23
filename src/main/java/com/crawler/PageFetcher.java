package com.crawler;

import java.util.Optional;

/**
 * Abstraction over HTTP fetching. Allows the Crawler to be tested with a fake
 * implementation that returns canned HTML without hitting the network.
 */
public interface PageFetcher {

    /**
     * Fetches the HTML content at the given URL.
     *
     * @return the response body if the request succeeded and the content is HTML;
     *         empty otherwise (non-2xx status, non-HTML content type, network error).
     */
    Optional<String> fetch(String url);
}
