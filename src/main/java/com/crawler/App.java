package com.crawler;

/**
 * CLI entry point. Usage:
 *   mvn exec:java -Dexec.args="https://crawlme.conco.com/"
 *   mvn exec:java -Dexec.args="https://crawlme.conco.com/ 8"
 */
public class App {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: crawler <seed-url> [max-threads]");
            System.exit(1);
        }

        String seedUrl = args[0];
        int maxThreads = args.length > 1 ? Integer.parseInt(args[1]) : 4;

        CrawlConfig config = CrawlConfig.builder()
                .maxConcurrency(maxThreads)
                .build();

        PageFetcher fetcher = new HttpPageFetcher(config);
        LinkExtractor extractor = new LinkExtractor();
        Crawler crawler = new Crawler(fetcher, extractor, config, App::printResult);

        System.out.printf("Starting crawl: %s (threads: %d)%n", seedUrl, maxThreads);
        System.out.println("---");

        try {
            crawler.crawl(seedUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Crawl interrupted.");
        }

        System.out.println("---");
        System.out.println("Crawl complete.");
    }

    private static synchronized void printResult(CrawlResult result) {
        System.out.println(result.url());
        if (result.status() == CrawlResult.Status.FETCH_ERROR) {
            System.out.println("  [failed to fetch]");
        } else {
            System.out.printf("  Links found: %d%n", result.links().size());
            for (String link : result.links()) {
                System.out.println("    " + link);
            }
        }
        System.out.println();
    }
}
