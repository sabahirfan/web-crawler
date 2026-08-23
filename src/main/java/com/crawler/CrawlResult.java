package com.crawler;

import java.util.List;

/**
 * The result of visiting a single URL. Contains all links discovered on the page,
 * regardless of whether they will be followed (the caller decides filtering).
 */
public record CrawlResult(String url, List<String> links, Status status) {

    public enum Status {
        SUCCESS,
        FETCH_ERROR
    }
}
