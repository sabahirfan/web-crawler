package com.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkExtractorTest {

    private final LinkExtractor extractor = new LinkExtractor();
    private static final String BASE = "https://example.com/current";

    @Test
    void extractsAbsoluteLinks() {
        String html = """
                <html><body>
                <a href="https://example.com/about">About</a>
                <a href="https://other.com/page">Other</a>
                </body></html>
                """;

        List<String> links = extractor.extract(html, BASE);

        assertThat(links).containsExactlyInAnyOrder(
                "https://example.com/about",
                "https://other.com/page"
        );
    }

    @Test
    void resolvesRelativeLinks() {
        String html = """
                <html><body>
                <a href="/about">About</a>
                <a href="contact">Contact</a>
                </body></html>
                """;

        List<String> links = extractor.extract(html, BASE);

        assertThat(links).containsExactlyInAnyOrder(
                "https://example.com/about",
                "https://example.com/contact"
        );
    }

    @Test
    void filtersOutNonHttpSchemes() {
        String html = """
                <html><body>
                <a href="mailto:user@example.com">Email</a>
                <a href="javascript:void(0)">JS</a>
                <a href="tel:+1234567890">Phone</a>
                <a href="ftp://files.example.com/doc">FTP</a>
                <a href="https://example.com/valid">Valid</a>
                </body></html>
                """;

        List<String> links = extractor.extract(html, BASE);

        assertThat(links).containsExactly("https://example.com/valid");
    }

    @Test
    void deduplicatesLinks() {
        String html = """
                <html><body>
                <a href="https://example.com/page">Link 1</a>
                <a href="https://example.com/page">Link 2</a>
                <a href="https://example.com/page#section">Link 3</a>
                </body></html>
                """;

        List<String> links = extractor.extract(html, BASE);

        assertThat(links).containsExactly("https://example.com/page");
    }

    @Test
    void handlesEmptyHref() {
        String html = """
                <html><body>
                <a href="">Empty</a>
                <a>No href</a>
                </body></html>
                """;

        List<String> links = extractor.extract(html, BASE);

        // Empty href resolves to the base URL
        assertThat(links).hasSize(1);
    }

    @Test
    void handlesNoLinks() {
        String html = "<html><body><p>No links here</p></body></html>";

        List<String> links = extractor.extract(html, BASE);

        assertThat(links).isEmpty();
    }

    @Test
    void normalizesExtractedLinks() {
        String html = """
                <html><body>
                <a href="https://EXAMPLE.COM:443/page/">Link</a>
                </body></html>
                """;

        List<String> links = extractor.extract(html, BASE);

        assertThat(links).containsExactly("https://example.com/page");
    }
}
