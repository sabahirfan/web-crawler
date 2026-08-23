package com.crawler;

import java.net.URI;

/**
 * URL normalization and subdomain matching utilities.
 *
 * Normalization ensures that equivalent URLs are treated as the same page,
 * preventing duplicate visits. For example:
 *   https://Example.COM/path/  →  https://example.com/path
 *   https://example.com:443/   →  https://example.com/
 *   https://example.com/page#section  →  https://example.com/page
 */
public final class UrlUtils {

    private UrlUtils() {}

    /**
     * Normalizes a URL for consistent deduplication:
     * - Lowercases scheme and host
     * - Removes default ports (80 for HTTP, 443 for HTTPS)
     * - Strips fragment identifiers (#...)
     * - Removes trailing slash (except for root path "/")
     * - Preserves query strings (different query = different page)
     */
    public static String normalize(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme().toLowerCase();
            String host = uri.getHost().toLowerCase();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getQuery();

            if ((scheme.equals("http") && port == 80)
                    || (scheme.equals("https") && port == 443)) {
                port = -1;
            }

            if (path == null || path.isEmpty()) {
                path = "/";
            }

            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port != -1) {
                sb.append(":").append(port);
            }
            sb.append(path);
            if (query != null && !query.isEmpty()) {
                sb.append("?").append(query);
            }

            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * Extracts the lowercase host from a URL.
     */
    public static String extractHost(String url) {
        try {
            return URI.create(url).getHost().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Returns true if the URL belongs to exactly the given host.
     * This enforces strict subdomain matching: "crawlme.conco.com" will NOT
     * match "conco.com", "www.crawlme.conco.com", or "community.conco.com".
     */
    public static boolean isSameSubdomain(String url, String allowedHost) {
        return extractHost(url).equals(allowedHost);
    }
}
