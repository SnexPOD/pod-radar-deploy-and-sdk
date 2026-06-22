package io.podradar.examples;

import io.podradar.crawler.CrawlerClient;
import io.podradar.crawler.model.FangguoAsset;
import io.podradar.crawler.model.FangguoAssetStatusCounts;
import io.podradar.crawler.model.FangguoCrawlStatus;
import io.podradar.crawler.model.FangguoItem;
import io.podradar.crawler.model.FangguoItemRetryResponse;
import io.podradar.crawler.model.FangguoItemsFilter;
import io.podradar.crawler.model.FangguoItemsResponse;
import io.podradar.crawler.model.FangguoLabel;
import io.podradar.crawler.model.FangguoRetryResponse;
import io.podradar.crawler.model.FangguoRun;
import io.podradar.crawler.model.FangguoRunRequest;
import io.podradar.crawler.model.FangguoRunResponse;
import io.podradar.crawler.model.FangguoRunsListResponse;
import io.podradar.crawler.model.FangguoSettingsResponse;
import io.podradar.crawler.model.FangguoStopResponse;
import io.podradar.sdk.error.PodRadarConflictException;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.model.PageQuery;

import java.time.Duration;
import java.util.List;

/**
 * 爬虫 SDK:方果(fangguo)run 生命周期完整调用,通过 {@code crawler.fangguo()} 子访问器。
 *
 * <pre>
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
 *   -Dexec.args="start-incremental --wait"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
 *   -Dexec.args="start-backfill 1780780800000 1780867200000 --dry-run"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
 *   -Dexec.args="list"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
 *   -Dexec.args="cross-items --q ABC123 --ship-status shipped --crawl-status failed"
 * </pre>
 */
public final class FangguoRunExample {

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }

        try (CrawlerClient crawler = ExampleSupport.crawlerBuilder()
                .retryOnServerError(false)
                .build()) {
            switch (args[0]) {
                case "settings":
                    settings(crawler);
                    break;
                case "start-incremental":
                    start(crawler, FangguoRunRequest.incremental(), args);
                    break;
                case "start-backfill":
                    start(crawler, backfillRequest(args), args);
                    break;
                case "list":
                    listRuns(crawler, args);
                    break;
                case "get":
                    getRun(crawler, args);
                    break;
                case "items":
                    listRunItems(crawler, args);
                    break;
                case "cross-items":
                    crossItems(crawler, args);
                    break;
                case "stop":
                    stop(crawler, args);
                    break;
                case "retry-failed-run":
                    retryFailedRun(crawler, args);
                    break;
                case "retry-failed-all":
                    retryFailedAll(crawler);
                    break;
                case "retry-item":
                    retryItem(crawler, args);
                    break;
                default:
                    usage();
                    System.exit(ExampleSupport.ERR_USAGE);
            }
        } catch (PodRadarException e) {
            ExampleSupport.exitPodRadarException("fangguo run op failed", e);
        }
    }

    private static FangguoRunRequest backfillRequest(String[] args) {
        List<String> pos = ExampleSupport.positional(args);
        if (pos.size() < 3) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }
        long from = ExampleSupport.requiredLong(pos.get(1), "from-ms");
        long to = ExampleSupport.requiredLong(pos.get(2), "to-ms");
        FangguoRunRequest req = FangguoRunRequest.backfill(from, to);
        if (ExampleSupport.hasFlag(args, "--dry-run")) req.withDryRun(true);
        return req;
    }

    private static void settings(CrawlerClient crawler) {
        FangguoSettingsResponse resp = crawler.fangguo().getSettings();
        System.out.printf("settings syncEnabled=%s interval=%dm overlap=%dm cursorStartAt=%s maxRunSpanHours=%d%n",
                resp.settings().syncEnabled(), resp.settings().syncIntervalMinutes(),
                resp.settings().syncOverlapMinutes(), ExampleSupport.nvl(resp.settings().cursorStartAt()),
                resp.settings().maxRunSpanHours());
        System.out.printf("state lastSuccessAt=%s lastRunId=%s nextSyncAt=%s%n",
                ExampleSupport.nvl(resp.state().lastSuccessAt()), ExampleSupport.nvl(resp.state().lastRunId()),
                ExampleSupport.nvl(resp.state().nextSyncAt()));
    }

    private static void start(CrawlerClient crawler, FangguoRunRequest req, String[] args)
            throws InterruptedException {
        try {
            FangguoRunResponse start = crawler.fangguo().startRun(req);
            System.out.printf("started runId=%d status=%s%n", start.runId(), start.status());
            if (ExampleSupport.hasFlag(args, "--wait")) {
                waitRun(crawler, start.runId());
            }
        } catch (PodRadarConflictException e) {
            System.err.println("another run already running: " + e.getMessage());
        }
    }

    private static void waitRun(CrawlerClient crawler, long runId) throws InterruptedException {
        while (true) {
            FangguoRun run = crawler.fangguo().getRun(runId);
            printRun(run);
            if (ExampleSupport.terminal(run.status())) break;
            Thread.sleep(Duration.ofSeconds(5).toMillis());
        }
    }

    private static void listRuns(CrawlerClient crawler, String[] args) {
        int limit = ExampleSupport.intOption(args, "--limit", 10);
        int offset = ExampleSupport.intOption(args, "--offset", 0);
        FangguoRunsListResponse runs = crawler.fangguo().listRuns(PageQuery.of(limit, offset));
        System.out.printf("runs total=%d limit=%d offset=%d%n", runs.total(), runs.limit(), runs.offset());
        runs.runs().forEach(FangguoRunExample::printRun);
    }

    private static void getRun(CrawlerClient crawler, String[] args) {
        printRun(crawler.fangguo().getRun(runIdArg(args)));
    }

    private static void listRunItems(CrawlerClient crawler, String[] args) {
        long runId = runIdArg(args);
        int limit = ExampleSupport.intOption(args, "--limit", 20);
        int offset = ExampleSupport.intOption(args, "--offset", 0);
        FangguoItemsResponse items = crawler.fangguo().listRunItems(runId, PageQuery.of(limit, offset));
        printItems(items);
    }

    private static void crossItems(CrawlerClient crawler, String[] args) {
        FangguoItemsFilter filter = FangguoItemsFilter.empty()
                .withPage(PageQuery.of(
                        ExampleSupport.intOption(args, "--limit", 20),
                        ExampleSupport.intOption(args, "--offset", 0)));
        String q = ExampleSupport.option(args, "--q", null);
        if (q != null) filter.withQuery(q);
        String shipStatus = ExampleSupport.option(args, "--ship-status", null);
        if (shipStatus != null) filter.withShipStatus(shipStatus);
        String crawlStatus = ExampleSupport.option(args, "--crawl-status", null);
        if (crawlStatus != null) filter.withCrawlStatus(parseCrawlStatus(crawlStatus));
        String runId = ExampleSupport.option(args, "--run-id", null);
        if (runId != null) filter.withRunId(ExampleSupport.requiredLong(runId, "run-id"));
        printItems(crawler.fangguo().listItems(filter));
    }

    private static FangguoCrawlStatus parseCrawlStatus(String raw) {
        for (FangguoCrawlStatus s : FangguoCrawlStatus.values()) {
            if (s.wire().equalsIgnoreCase(raw)) return s;
        }
        System.err.println("crawl-status must be one of ok|failed|partial: " + raw);
        System.exit(ExampleSupport.ERR_USAGE);
        return null;
    }

    private static void stop(CrawlerClient crawler, String[] args) {
        long runId = runIdArg(args);
        boolean pauseAuto = ExampleSupport.hasFlag(args, "--pause-auto");
        try {
            FangguoStopResponse resp = crawler.fangguo().stopRun(runId, pauseAuto);
            System.out.printf("stop status=%s runId=%d pauseAuto=%s stoppedAssets=%d stoppedLabels=%d%n",
                    resp.status(), resp.runId(), resp.pauseAuto(), resp.stoppedAssets(), resp.stoppedLabels());
        } catch (PodRadarConflictException e) {
            System.err.println("run is not running: " + e.getMessage());
        }
    }

    private static void retryFailedRun(CrawlerClient crawler, String[] args) {
        printRetry("retry-run", crawler.fangguo().retryFailedRun(runIdArg(args)));
    }

    private static void retryFailedAll(CrawlerClient crawler) {
        printRetry("retry-all", crawler.fangguo().retryFailedAll());
    }

    private static void retryItem(CrawlerClient crawler, String[] args) {
        long itemId = runIdArg(args);
        FangguoItemRetryResponse resp = crawler.fangguo().retryItem(itemId);
        System.out.printf("retry-item status=%s orderUnitId=%d requeuedAssets=%d requeuedLabels=%d%n",
                resp.status(), resp.orderUnitId(), resp.requeuedAssets(), resp.requeuedLabels());
    }

    // ───── printing helpers ───────────────────────────────────────────

    private static void printRun(FangguoRun run) {
        System.out.printf("run id=%d status=%s trigger=%s mode=%s queued=%d fetched=%d failed=%d duplicate=%d%n",
                run.id(), ExampleSupport.nvl(run.status()), ExampleSupport.nvl(run.trigger()),
                ExampleSupport.nvl(run.mode()), run.queued(), run.fetched(), run.failed(), run.duplicate());
        if (run.error() != null) System.out.println("  error=" + run.error());
        FangguoAssetStatusCounts effect = run.live().effectImage();
        FangguoAssetStatusCounts production = run.live().productionImage();
        FangguoAssetStatusCounts finished = run.live().finishedImage();
        System.out.printf("  live effect=%d/%d/%d/%d production=%d/%d/%d/%d finished=%d/%d/%d/%d (pending/fetching/fetched/failed)%n",
                effect.pending(), effect.fetching(), effect.fetched(), effect.failed(),
                production.pending(), production.fetching(), production.fetched(), production.failed(),
                finished.pending(), finished.fetching(), finished.fetched(), finished.failed());
        if (!run.live().labels().isEmpty()) System.out.println("  labels=" + run.live().labels());
    }

    private static void printItems(FangguoItemsResponse items) {
        System.out.printf("items total=%d limit=%d offset=%d detailSource=%s run=%s%n",
                items.total(), items.limit(), items.offset(), ExampleSupport.nvl(items.detailSource()),
                items.run() == null ? "-" : items.run().id());
        for (FangguoItem it : items.items()) {
            System.out.printf("item id=%d orderId=%d tid=%s unitKey=%s barcode=%s coverStatus=%s ship=%s%n",
                    it.id(), it.orderId(), ExampleSupport.nvl(it.tid()), ExampleSupport.nvl(it.unitKey()),
                    ExampleSupport.nvl(it.barcode()), ExampleSupport.nvl(it.coverStatus()),
                    ExampleSupport.nvl(it.order().shipStatus()));
            for (FangguoAsset a : it.assets()) {
                System.out.printf("     asset kind=%s status=%s full=%s error=%s%n",
                        ExampleSupport.nvl(a.assetKind()), ExampleSupport.nvl(a.status()),
                        ExampleSupport.nvl(a.full()), ExampleSupport.nvl(a.lastError()));
            }
            for (FangguoLabel l : it.labels()) {
                System.out.printf("     label pkg=%s carrier=%s status=%s pdf=%s%n",
                        ExampleSupport.nvl(l.packageNo()), ExampleSupport.nvl(l.carrier()),
                        ExampleSupport.nvl(l.status()), ExampleSupport.nvl(l.pdf()));
            }
        }
    }

    private static void printRetry(String label, FangguoRetryResponse resp) {
        System.out.printf("%s status=%s runId=%s requeuedAssets=%d requeuedLabels=%d itemCount=%d%n",
                label, resp.status(), ExampleSupport.nvl(resp.runId()),
                resp.requeuedAssets(), resp.requeuedLabels(), resp.itemCount());
    }

    private static long runIdArg(String[] args) {
        List<String> pos = ExampleSupport.positional(args);
        if (pos.size() < 2) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }
        return ExampleSupport.requiredLong(pos.get(1), "id");
    }

    private static void usage() {
        System.err.println("usage:");
        System.err.println("  FangguoRunExample settings");
        System.err.println("  FangguoRunExample start-incremental [--wait] [--dry-run]");
        System.err.println("  FangguoRunExample start-backfill <from-ms> <to-ms> [--dry-run] [--wait]");
        System.err.println("  FangguoRunExample list [--limit N] [--offset N]");
        System.err.println("  FangguoRunExample get <run-id>");
        System.err.println("  FangguoRunExample items <run-id> [--limit N] [--offset N]");
        System.err.println("  FangguoRunExample cross-items [--run-id N] [--q TEXT] [--ship-status S] [--crawl-status ok|failed|partial] [--limit N] [--offset N]");
        System.err.println("  FangguoRunExample stop <run-id> [--pause-auto]");
        System.err.println("  FangguoRunExample retry-failed-run <run-id>");
        System.err.println("  FangguoRunExample retry-failed-all");
        System.err.println("  FangguoRunExample retry-item <item-id>");
    }

    private FangguoRunExample() {}
}
