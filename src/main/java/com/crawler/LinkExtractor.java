package com.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

/**
 * Extracts and normalizes all HTTP(S) links from an HTML document.
 *
 * Uses Jsoup's absUrl() to resolve relative links against the page's base URL,
 * then normalizes each link for consistent deduplication downstream.
 *
 * Non-HTTP schemes (mailto:, javascript:, tel:, etc.) are filtered out since
 * they are not crawlable web pages.
 */
public class LinkExtractor {

    /**
     * @param html    the raw HTML content of the page
     * @param baseUrl the URL the page was fetched from (used to resolve relative links)
     * @return a deduplicated list of normalized absolute HTTP(S) URLs found on the page
     */
    public List<String> extract(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);

        return doc.select("a[href]").stream()
                .map(anchor -> anchor.absUrl("href"))
                .filter(url -> !url.isEmpty())
                .filter(url -> url.startsWith("http://") || url.startsWith("https://"))
                .map(UrlUtils::normalize)
                .distinct()
                .toList();
    }
}
