# Web Crawler

A concurrent web crawler that starts from a seed URL and discovers all pages within the same subdomain. Built with plain Java and no framework dependencies.

## Prerequisites

- Java 17 or later

## Build

```bash
./gradlew build
```

## Run

```bash
./gradlew run --args='<seed-url> [max-threads]'
```

**Arguments:**

| Argument | Required | Default | Description |
|---|---|---|---|
| `seed-url` | Yes | - | The URL to start crawling from |
| `max-threads` | No | 4 | Number of concurrent crawler threads |

**Examples:**

```bash
# Crawl with default settings (4 threads)
./gradlew run --args='https://example.com/'

# Crawl with 8 threads
./gradlew run --args='https://example.com/ 8'
```

The crawler prints each visited page along with all links found on it:

```
Starting crawl: https://example.com/ (threads: 4)
---
https://example.com/
  Links found: 3
    https://example.com/about
    https://example.com/blog
    https://external-site.com/ref

https://example.com/about
  Links found: 1
    https://example.com/
---
Crawl complete.
```

## Run Tests

```bash
./gradlew test
```

## How It Works

The crawler stays within the exact subdomain of the seed URL. Starting from `https://blog.example.com/`, it will follow links on `blog.example.com` but not `example.com` or `www.example.com`.

Default configuration:

| Setting | Default |
|---|---|
| Concurrency | 4 threads |
| Request timeout | 10 seconds |
| Politeness delay | 100ms per request |
| Max pages | 1000 |

See [DESIGN.md](DESIGN.md) for architecture details, design decisions, and potential improvements.

## License

Apache 2.0
