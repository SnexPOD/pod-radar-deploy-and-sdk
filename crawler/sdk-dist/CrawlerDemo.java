import io.podradar.crawler.CrawlerClient;
import io.podradar.crawler.model.CrawlStatus;
import io.podradar.crawler.model.HihumbirdItem;
import io.podradar.crawler.model.ItemsFilter;
import io.podradar.crawler.model.ItemsListResponse;
import io.podradar.crawler.model.RetryFailedKind;
import io.podradar.crawler.model.RetryFailedKindRequest;
import io.podradar.crawler.model.RetryFailedKindResponse;
import io.podradar.crawler.model.RunRequest;
import io.podradar.crawler.model.RunResponse;
import io.podradar.crawler.model.RunSummary;
import io.podradar.crawler.model.RunsListResponse;
import io.podradar.sdk.model.PageQuery;

/**
 * 爬虫系统 Java SDK Demo（独立可运行，仅依赖 crawler-sdk + sdk-core 两个 jar）。
 *
 * 编译运行见同目录 README.md。命令：
 *   list   [limit]                          列最近的同步 run
 *   items  [ok|failed|partial|product_image_failed|shipping_label_failed|source_image_failed|production_image_failed]
 *          [limit] [created_from_ms] [created_to_ms]
 *                                           跨 run 查 item（顺带打印历史订单门天数）
 *   start                                   触发一次增量同步 run
 *   retry  <product_image|production_image|source_image|label> [run_id]
 *                                           按类型批量重试失败素材（只重试 N 天内的订单）
 *   cursor <ISO时间|null>                    强制游标到某时间 / null=清空
 */
public final class CrawlerDemo {
    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            System.exit(2);
        }
        String endpoint = requireEnv("CRAWLER_ENDPOINT");
        String apiKey = requireEnv("CRAWLER_API_KEY");

        try (CrawlerClient crawler = CrawlerClient.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .build()) {
            switch (args[0]) {
                case "list":   list(crawler, intArg(args, 1, 10)); break;
                case "items":  items(crawler, args); break;
                case "start":  start(crawler); break;
                case "retry":  retry(crawler, args); break;
                case "cursor": cursor(crawler, args); break;
                default:
                    usage();
                    System.exit(2);
            }
        }
    }

    private static void list(CrawlerClient crawler, int limit) {
        RunsListResponse runs = crawler.listRuns(PageQuery.of(limit, 0));
        System.out.printf("runs total=%d limit=%d offset=%d%n", runs.total(), runs.limit(), runs.offset());
        for (RunSummary r : runs.runs()) {
            System.out.printf("#%d %s/%s status=%s queued=%d fetched=%d failed=%d%n",
                    r.id(), r.trigger(), r.mode(), r.status(), r.queued(), r.fetched(), r.failed());
        }
    }

    private static void items(CrawlerClient crawler, String[] args) {
        ItemsFilter filter = ItemsFilter.empty().withPage(PageQuery.of(intArg(args, 2, 20), 0));
        if (args.length >= 2) filter.withCrawlStatus(parseStatus(args[1]));
        if (args.length >= 4) filter.withCreatedFrom(longArg(args, 3, "created_from_ms"));
        if (args.length >= 5) filter.withCreatedTo(longArg(args, 4, "created_to_ms"));
        ItemsListResponse items = crawler.listItems(filter);
        System.out.printf("items total=%d limit=%d offset=%d historyOrderDays=%s%n",
                items.total(), items.limit(), items.offset(),
                items.historyOrderDays().isPresent() ? items.historyOrderDays().getAsInt() : "-");
        for (HihumbirdItem it : items.items()) {
            System.out.printf("%d %-16s %-8s %s%n",
                    it.id(), nvl(it.assetKind()), nvl(it.status()), nvl(it.productionOrderItemCode()));
        }
    }

    private static void start(CrawlerClient crawler) {
        RunResponse r = crawler.startRun(RunRequest.incremental());
        System.out.printf("started runId=%d status=%s itemCount=%s%n",
                r.runId(), r.status(), r.itemCount().isPresent() ? r.itemCount().getAsInt() : "-");
    }

    private static void retry(CrawlerClient crawler, String[] args) {
        if (args.length < 2) { usage(); System.exit(2); }
        RetryFailedKind kind = RetryFailedKind.valueOf(args[1].toUpperCase());
        RetryFailedKindRequest req = RetryFailedKindRequest.of(kind);
        if (args.length >= 3) req.withRunId(Long.parseLong(args[2]));
        RetryFailedKindResponse r = crawler.retryFailedKind(req);
        System.out.printf("retry kind=%s status=%s queued=%d reharvest=%s batches=%s runIds=%s%n",
                r.kind(), r.status(), r.queued(), r.reharvest(),
                r.batches().isPresent() ? r.batches().getAsInt() : "-", r.runIds());
        System.out.println("（提示：批量重试只重试创建时间在 historyOrderDays 天内的订单，更早的老订单跳过）");
    }

    private static void cursor(CrawlerClient crawler, String[] args) {
        if (args.length < 2) { usage(); System.exit(2); }
        String at = "null".equalsIgnoreCase(args[1]) ? null : args[1];
        crawler.setCursor(at);
        System.out.println(at == null ? "游标已清空（下次增量走起始游标/兜底窗口）" : "游标已强制设到 " + at);
    }

    private static CrawlStatus parseStatus(String v) {
        switch (v.toLowerCase()) {
            case "ok":      return CrawlStatus.OK;
            case "failed":  return CrawlStatus.FAILED;
            case "partial": return CrawlStatus.PARTIAL;
            case "product_image_failed": return CrawlStatus.PRODUCT_IMAGE_FAILED;
            case "shipping_label_failed": return CrawlStatus.SHIPPING_LABEL_FAILED;
            case "source_image_failed": return CrawlStatus.SOURCE_IMAGE_FAILED;
            case "production_image_failed": return CrawlStatus.PRODUCTION_IMAGE_FAILED;
            default:        throw new IllegalArgumentException("unknown crawl status: " + v);
        }
    }

    private static int intArg(String[] args, int idx, int dflt) {
        if (args.length <= idx) return dflt;
        try { return Integer.parseInt(args[idx]); } catch (NumberFormatException e) { return dflt; }
    }

    private static long longArg(String[] args, int idx, String label) {
        try {
            return Long.parseLong(args[idx]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be epoch ms: " + args[idx]);
        }
    }

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.trim().isEmpty()) throw new IllegalStateException("missing env: " + name);
        return v;
    }

    private static String nvl(Object v) { return v == null ? "" : String.valueOf(v); }

    private static void usage() {
        System.err.println("usage:");
        System.err.println("  java CrawlerDemo list [limit]");
        System.err.println("  java CrawlerDemo items [ok|failed|partial|product_image_failed|shipping_label_failed|source_image_failed|production_image_failed] [limit] [created_from_ms] [created_to_ms]");
        System.err.println("  java CrawlerDemo start");
        System.err.println("  java CrawlerDemo retry <product_image|production_image|source_image|label> [run_id]");
        System.err.println("  java CrawlerDemo cursor <ISO时间|null>");
    }

    private CrawlerDemo() {}
}
