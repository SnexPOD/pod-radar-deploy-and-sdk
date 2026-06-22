package io.podradar.examples;

import io.podradar.crawler.CrawlerClient;
import io.podradar.crawler.model.HihumbirdItem;
import io.podradar.crawler.model.RunSummary;
import io.podradar.sdk.PodRadarClient;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.error.PodRadarRateLimitException;
import io.podradar.sdk.error.PodRadarValidationException;
import io.podradar.sdk.model.BatchUploadResponse;
import io.podradar.sdk.model.ImageDto;
import io.podradar.sdk.model.ImageRef;
import io.podradar.sdk.model.SearchResponse;
import io.podradar.sdk.model.WriteJobItemsResponse;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ExampleSupport {
    static final int OK = 0;
    static final int ERR_RUNTIME = 1;
    static final int ERR_USAGE = 2;
    static final int ERR_RATE_LIMIT = 3;
    static final int ERR_VALIDATION = 4;

    private static final Set<String> OPTIONS_WITH_VALUE = new HashSet<>(Arrays.asList(
            "--batch",
            "--crawl-status",
            "--cursor-start-at",
            "--created-from",
            "--created-to",
            "--k",
            "--limit",
            "--max-run-span-hours",
            "--mime",
            "--min-score",
            "--offset",
            "--production-order-item",
            "--production-from",
            "--production-to",
            "--q",
            "--rescan-enabled",
            "--rescan-interval",
            "--rescan-max-age-days",
            "--run-id",
            "--sales-order",
            "--ship-status",
            "--source",
            "--source-id",
            "--status",
            "--status-name",
            "--sync-enabled",
            "--sync-interval",
            "--sync-overlap",
            "--tags",
            "--text",
            "--title",
            "--track"));

    static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            System.err.println("missing env: " + name);
            System.exit(ERR_USAGE);
        }
        return value;
    }

    static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    static PodRadarClient.Builder podRadarBuilder() {
        return PodRadarClient.builder()
                .endpoint(requiredEnv("POD_RADAR_ENDPOINT"))
                .apiKey(requiredEnv("POD_RADAR_API_KEY"))
                .connectTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofSeconds(30))
                .userAgent(envOr("POD_RADAR_USER_AGENT", "podradar-examples/0.1.0"));
    }

    static CrawlerClient.Builder crawlerBuilder() {
        return CrawlerClient.builder()
                .endpoint(requiredEnv("POD_RADAR_CRAWLER_ENDPOINT"))
                .apiKey(requiredEnv("POD_RADAR_CRAWLER_KEY"))
                .connectTimeout(Duration.ofSeconds(5))
                .requestTimeout(Duration.ofMinutes(2))
                .userAgent(envOr("POD_RADAR_CRAWLER_USER_AGENT", "podradar-crawler-examples/0.1.0"));
    }

    static File requiredFile(String path) {
        File f = new File(path);
        if (!f.isFile()) {
            System.err.println("not a file: " + f);
            System.exit(ERR_USAGE);
        }
        return f;
    }

    static long requiredLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            System.err.println(label + " must be a number: " + value);
            System.exit(ERR_USAGE);
            return -1L;
        }
    }

    static int intOption(String[] args, String name, int fallback) {
        String value = option(args, name, null);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println(name + " must be an integer: " + value);
            System.exit(ERR_USAGE);
            return fallback;
        }
    }

    static double doubleOption(String[] args, String name, double fallback) {
        String value = option(args, name, null);
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println(name + " must be a number: " + value);
            System.exit(ERR_USAGE);
            return fallback;
        }
    }

    static boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) return true;
        }
        return false;
    }

    static String option(String[] args, String name, String fallback) {
        String prefix = name + "=";
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(prefix)) return args[i].substring(prefix.length());
            if (name.equals(args[i]) && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    static List<String> positional(String[] args) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                if (!arg.contains("=") && OPTIONS_WITH_VALUE.contains(arg)
                        && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    i++;
                }
                continue;
            }
            out.add(arg);
        }
        return out;
    }

    static List<URI> urlsFromArgsOrEnv(String[] args, String envName, List<URI> fallback) {
        List<String> values = positional(args);
        List<URI> urls = new ArrayList<>();
        for (String v : values) {
            if (v.startsWith("http://") || v.startsWith("https://")) urls.add(URI.create(v));
        }
        if (!urls.isEmpty()) return urls;

        String raw = System.getenv(envName);
        if (raw != null && !raw.trim().isEmpty()) {
            Arrays.stream(raw.split("[,\\s]+"))
                    .filter(s -> !s.trim().isEmpty())
                    .map(URI::create)
                    .forEach(urls::add);
        }
        return urls.isEmpty() ? fallback : urls;
    }

    static void printSearchResponse(SearchResponse resp) {
        System.out.printf("model=%s  k=%d  minScore=%.2f  hits=%d%n",
                resp.model(), resp.k(), resp.minScore(), resp.results().size());
        for (ImageRef h : resp.results()) {
            System.out.printf("  id=%-8d score=%.4f status=%s source=%s sourceId=%s title=%s%n",
                    h.imageId(), h.score(), nvl(h.status()), nvl(h.source()), nvl(h.sourceId()), nvl(h.title()));
            System.out.printf("      thumb=%s%n", nvl(h.thumb()));
            System.out.printf("      full =%s%n", nvl(h.full()));
        }
    }

    static void printImage(ImageDto image) {
        System.out.printf("image id=%d status=%s source=%s sourceId=%s title=%s%n",
                image.imageId(), nvl(image.status()), nvl(image.source()), nvl(image.sourceId()), nvl(image.title()));
        System.out.printf("      size=%sx%s bytes=%s mime=%s duplicateOf=%s%n",
                nvl(image.width()), nvl(image.height()), nvl(image.bytes()), nvl(image.mime()), nvl(image.duplicateOf()));
        System.out.printf("      tags=%s%n", image.tags());
        System.out.printf("      thumb=%s%n", nvl(image.thumb()));
        System.out.printf("      full =%s%n", nvl(image.full()));
    }

    static void printBatchUpload(BatchUploadResponse r) {
        System.out.printf("job=%s total=%d succeeded=%d failed=%d duplicate=%d%n",
                r.jobId(), r.total(), r.succeeded(), r.failed(), r.duplicate());
        for (BatchUploadResponse.Item item : r.results()) {
            System.out.printf("  url=%s imageId=%s created=%s duplicate=%s error=%s%n",
                    item.imageUrl(), nvl(item.imageId()), item.created(), item.duplicate(), nvl(item.error()));
        }
    }

    static void printWriteJobItems(WriteJobItemsResponse items) {
        System.out.printf("items total=%d limit=%d offset=%d%n", items.total(), items.limit(), items.offset());
        for (WriteJobItemsResponse.Item it : items.items()) {
            System.out.printf("  #%d id=%d mode=%s status=%s attempts=%d imageId=%s error=%s%n",
                    it.itemIndex(), it.id(), nvl(it.uploadMode()), nvl(it.status()), it.attempts(),
                    nvl(it.imageId()), nvl(it.lastError()));
        }
    }

    static void printRunSummary(RunSummary run) {
        System.out.printf("run id=%d status=%s trigger=%s mode=%s queued=%d fetched=%d failed=%d duplicate=%d%n",
                run.id(), nvl(run.status()), nvl(run.trigger()), nvl(run.mode()),
                run.queued(), run.fetched(), run.failed(), run.duplicate());
        if (run.error() != null) System.out.println("  error=" + run.error());
        if (!run.failures().isEmpty()) {
            run.failures().forEach(f -> System.out.printf("  failure %dx %s%n", f.count(), f.error()));
        }
    }

    static void printCrawlerItem(HihumbirdItem it) {
        System.out.printf("item id=%d status=%s kind=%s external=%s sales=%s batch=%s track=%s%n",
                it.id(), nvl(it.status()), nvl(it.assetKind()), nvl(it.externalKey()),
                nvl(it.salesOrderNo()), nvl(it.productionBatchCode()), nvl(it.trackNumber()));
        System.out.printf("     title=%s style=%s/%s color=%s size=%s%n",
                nvl(it.title()), nvl(it.styleCode()), nvl(it.styleName()), nvl(it.color()), nvl(it.size()));
        System.out.printf("     thumb=%s full=%s error=%s%n", nvl(it.thumb()), nvl(it.full()), nvl(it.lastError()));
    }

    static boolean terminal(String status) {
        if (status == null) return false;
        String s = status.toLowerCase(Locale.ROOT);
        return !"running".equals(s) && !"pending".equals(s) && !"queued".equals(s);
    }

    static void exitPodRadarException(String label, PodRadarException e) {
        if (e instanceof PodRadarRateLimitException) {
            PodRadarRateLimitException rl = (PodRadarRateLimitException) e;
            System.err.println(label + ": rate limited; retry after " + rl.retryAfterSeconds() + "s");
            System.exit(ERR_RATE_LIMIT);
        }
        if (e instanceof PodRadarValidationException) {
            PodRadarValidationException ve = (PodRadarValidationException) e;
            System.err.println(label + ": bad request: " + ve.error() + " request_id=" + ve.requestId()
                    + " details=" + ve.details());
            System.exit(ERR_VALIDATION);
        }
        System.err.println(label + ": " + e.getMessage());
        System.exit(ERR_RUNTIME);
    }

    static String nvl(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private ExampleSupport() {}
}
