package io.podradar.examples;

import io.podradar.crawler.CrawlerClient;
import io.podradar.crawler.model.ItemsListResponse;
import io.podradar.crawler.model.RescanResponse;
import io.podradar.crawler.model.RetryRunResponse;
import io.podradar.crawler.model.RunRequest;
import io.podradar.crawler.model.RunResponse;
import io.podradar.crawler.model.RunSummary;
import io.podradar.crawler.model.RunsListResponse;
import io.podradar.sdk.error.PodRadarConflictException;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.model.PageQuery;

import java.time.Duration;
import java.util.List;

/**
 * 爬虫 SDK:run 生命周期完整调用。
 *
 * <pre>
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
 *   -Dexec.args="start-incremental --wait"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
 *   -Dexec.args="start-backfill 1780780800000 1780867200000 --batch B-20260607 --dry-run"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
 *   -Dexec.args="list"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
 *   -Dexec.args="rescan-missing-batches --account-id 9"
 * </pre>
 */
public final class CrawlerRunExample {

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }

        try (CrawlerClient crawler = ExampleSupport.crawlerBuilder()
                .retryOnServerError(false)
                .build()) {
            switch (args[0]) {
                case "start-incremental":
                    start(crawler, RunRequest.incremental(), args);
                    break;
                case "start-backfill":
                    start(crawler, backfillRequest(args), args);
                    break;
                case "list":
                    listRuns(crawler, args);
                    break;
                case "items":
                    listRunItems(crawler, args);
                    break;
                case "retry-failed-run":
                    retryFailedRun(crawler, args);
                    break;
                case "rescan-pending":
                    rescanPending(crawler);
                    break;
                case "rescan-missing-batches":
                    rescanMissingBatches(crawler, args);
                    break;
                default:
                    usage();
                    System.exit(ExampleSupport.ERR_USAGE);
            }
        } catch (PodRadarException e) {
            ExampleSupport.exitPodRadarException("crawler run op failed", e);
        }
    }

    private static RunRequest backfillRequest(String[] args) {
        List<String> pos = ExampleSupport.positional(args);
        if (pos.size() < 3) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }
        long from = ExampleSupport.requiredLong(pos.get(1), "from-ms");
        long to = ExampleSupport.requiredLong(pos.get(2), "to-ms");
        RunRequest req = RunRequest.backfill(from, to);
        String batch = ExampleSupport.option(args, "--batch", null);
        if (batch != null) req.withBatchCode(batch);
        if (ExampleSupport.hasFlag(args, "--dry-run")) req.withDryRun(true);
        return req;
    }

    private static void start(CrawlerClient crawler, RunRequest req, String[] args) throws InterruptedException {
        try {
            RunResponse start = crawler.startRun(req);
            System.out.printf("started runId=%d status=%s itemCount=%s%n",
                    start.runId(), start.status(),
                    start.itemCount().isPresent() ? start.itemCount().getAsInt() : "-");
            if (ExampleSupport.hasFlag(args, "--wait")) {
                waitRun(crawler, start.runId());
            }
        } catch (PodRadarConflictException e) {
            System.err.println("another run already running: " + e.getMessage());
        }
    }

    private static void waitRun(CrawlerClient crawler, long runId) throws InterruptedException {
        while (true) {
            RunSummary run = findRun(crawler, runId);
            ExampleSupport.printRunSummary(run);
            if (ExampleSupport.terminal(run.status())) break;
            Thread.sleep(Duration.ofSeconds(5).toMillis());
        }
    }

    private static RunSummary findRun(CrawlerClient crawler, long runId) {
        PageQuery page = PageQuery.of(50, 0);
        while (true) {
            RunsListResponse runs = crawler.listRuns(page);
            for (RunSummary run : runs.runs()) {
                if (run.id() == runId) return run;
            }
            if (runs.offset() + runs.runs().size() >= runs.total()) break;
            page = page.next();
        }
        throw new IllegalStateException("run " + runId + " not found");
    }

    private static void listRuns(CrawlerClient crawler, String[] args) {
        int limit = ExampleSupport.intOption(args, "--limit", 10);
        int offset = ExampleSupport.intOption(args, "--offset", 0);
        RunsListResponse runs = crawler.listRuns(PageQuery.of(limit, offset));
        System.out.printf("runs total=%d limit=%d offset=%d%n", runs.total(), runs.limit(), runs.offset());
        runs.runs().forEach(ExampleSupport::printRunSummary);
    }

    private static void listRunItems(CrawlerClient crawler, String[] args) {
        List<String> pos = ExampleSupport.positional(args);
        if (pos.size() < 2) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }
        long runId = ExampleSupport.requiredLong(pos.get(1), "run-id");
        int limit = ExampleSupport.intOption(args, "--limit", 20);
        int offset = ExampleSupport.intOption(args, "--offset", 0);
        ItemsListResponse items = crawler.listRunItems(runId, PageQuery.of(limit, offset));
        System.out.printf("runId=%s total=%d limit=%d offset=%d%n",
                ExampleSupport.nvl(items.runId()), items.total(), items.limit(), items.offset());
        items.items().forEach(ExampleSupport::printCrawlerItem);
    }

    private static void retryFailedRun(CrawlerClient crawler, String[] args) {
        List<String> pos = ExampleSupport.positional(args);
        if (pos.size() < 2) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }
        RetryRunResponse retry = crawler.retryFailedRun(ExampleSupport.requiredLong(pos.get(1), "run-id"));
        System.out.printf("retry runId=%d status=%s itemCount=%s%n",
                retry.runId(), retry.status(),
                retry.itemCount().isPresent() ? retry.itemCount().getAsInt() : "-");
    }

    private static void rescanPending(CrawlerClient crawler) {
        RescanResponse resp = crawler.rescanPendingLabels();
        System.out.printf("rescan runId=%d status=%s itemCount=%s%n",
                resp.runId(), resp.status(),
                resp.itemCount().isPresent() ? resp.itemCount().getAsInt() : "-");
    }

    private static void rescanMissingBatches(CrawlerClient crawler, String[] args) {
        String accountId = ExampleSupport.option(args, "--account-id", null);
        RescanResponse resp = accountId == null
                ? crawler.rescanMissingBatches()
                : crawler.rescanMissingBatches(ExampleSupport.requiredLong(accountId, "account-id"));
        System.out.printf("missing-batch rescan runId=%d status=%s itemCount=%s%n",
                resp.runId(), resp.status(),
                resp.itemCount().isPresent() ? resp.itemCount().getAsInt() : "-");
        if (resp.isQueued()) {
            System.out.println("queued: another hihumbird sync is active; the server will run this after the lock is free");
        }
    }

    private static void usage() {
        System.err.println("usage:");
        System.err.println("  CrawlerRunExample start-incremental [--wait]");
        System.err.println("  CrawlerRunExample start-backfill <from-ms> <to-ms> [--batch CODE] [--dry-run] [--wait]");
        System.err.println("  CrawlerRunExample list [--limit N] [--offset N]");
        System.err.println("  CrawlerRunExample items <run-id> [--limit N] [--offset N]");
        System.err.println("  CrawlerRunExample retry-failed-run <run-id>");
        System.err.println("  CrawlerRunExample rescan-pending");
        System.err.println("  CrawlerRunExample rescan-missing-batches [--account-id ID]");
    }

    private CrawlerRunExample() {}
}
