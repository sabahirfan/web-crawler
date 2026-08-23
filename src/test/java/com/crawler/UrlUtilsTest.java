package com.crawler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlUtilsTest {

    @Nested
    class Normalize {

        @Test
        void removesFragment() {
            assertThat(UrlUtils.normalize("https://example.com/page#section"))
                    .isEqualTo("https://example.com/page");
        }

        @Test
        void removesTrailingSlash() {
            assertThat(UrlUtils.normalize("https://example.com/page/"))
                    .isEqualTo("https://example.com/page");
        }

        @Test
        void preservesRootTrailingSlash() {
            assertThat(UrlUtils.normalize("https://example.com/"))
                    .isEqualTo("https://example.com/");
        }

        @Test
        void lowercasesHost() {
            assertThat(UrlUtils.normalize("https://EXAMPLE.COM/Page"))
                    .isEqualTo("https://example.com/Page");
        }

        @Test
        void removesDefaultHttpPort() {
            assertThat(UrlUtils.normalize("http://example.com:80/page"))
                    .isEqualTo("http://example.com/page");
        }

        @Test
        void removesDefaultHttpsPort() {
            assertThat(UrlUtils.normalize("https://example.com:443/page"))
                    .isEqualTo("https://example.com/page");
        }

        @Test
        void preservesNonDefaultPort() {
            assertThat(UrlUtils.normalize("https://example.com:8080/page"))
                    .isEqualTo("https://example.com:8080/page");
        }

        @Test
        void addsSlashForEmptyPath() {
            assertThat(UrlUtils.normalize("https://example.com"))
                    .isEqualTo("https://example.com/");
        }

        @Test
        void preservesQueryString() {
            assertThat(UrlUtils.normalize("https://example.com/search?q=test"))
                    .isEqualTo("https://example.com/search?q=test");
        }

        @Test
        void removesFragmentButKeepsQuery() {
            assertThat(UrlUtils.normalize("https://example.com/page?q=1#top"))
                    .isEqualTo("https://example.com/page?q=1");
        }
    }

    @Nested
    class SubdomainMatching {

        @Test
        void sameHostMatches() {
            assertThat(UrlUtils.isSameSubdomain("https://crawlme.conco.com/page", "crawlme.conco.com"))
                    .isTrue();
        }

        @Test
        void parentDomainDoesNotMatch() {
            assertThat(UrlUtils.isSameSubdomain("https://conco.com/page", "crawlme.conco.com"))
                    .isFalse();
        }

        @Test
        void differentSubdomainDoesNotMatch() {
            assertThat(UrlUtils.isSameSubdomain("https://community.conco.com/page", "crawlme.conco.com"))
                    .isFalse();
        }

        @Test
        void externalDomainDoesNotMatch() {
            assertThat(UrlUtils.isSameSubdomain("https://facebook.com/conco", "crawlme.conco.com"))
                    .isFalse();
        }

        @Test
        void matchingIsCaseInsensitive() {
            assertThat(UrlUtils.isSameSubdomain("https://CRAWLME.CONCO.COM/page", "crawlme.conco.com"))
                    .isTrue();
        }
    }

    @Nested
    class ExtractHost {

        @Test
        void extractsHostFromUrl() {
            assertThat(UrlUtils.extractHost("https://crawlme.conco.com/path"))
                    .isEqualTo("crawlme.conco.com");
        }

        @Test
        void returnsEmptyForMalformedUrl() {
            assertThat(UrlUtils.extractHost("not-a-url"))
                    .isEmpty();
        }
    }
}
