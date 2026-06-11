package io.podradar.examples;

import io.podradar.crawler.CrawlerClient;
import io.podradar.crawler.model.CrawlStatus;
import io.podradar.crawler.model.ItemRetryResponse;
import io.podradar.crawler.model.ItemsFilter;
import io.podradar.crawler.model.ItemsListResponse;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.model.PageQuery;

import java.util.List;

/**
 * 爬虫 SDK:跨 run 查询 item + 单条重抓。
 *
 * <pre>
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerItemsExample \
 *   -Dexec.args="list --status failed --sales-order SO-123 --limit 20"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerItemsExample \
 *   -Dexec.args="retry-item 98765"
 * </pre>
 */
public final class CrawlerItemsExample {

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }

        try (CrawlerClient crawler = ExampleSupport.crawlerBuilder().build()) {
            switch (args[0]) {
                case "list":
                    listItems(crawler, args);
                    break;
                case "retry-item":
                    retryItem(crawler, args);
                    break;
                default:
                    usage();
                    System.exit(ExampleSupport.ERR_USAGE);
            }
        } catch (PodRadarException e) {
            ExampleSupport.exitPodRadarException("crawler item op failed", e);
        }
    }

    private static void listItems(CrawlerClient crawler, String[] args) {
        ItemsFilter filter = ItemsFilter.empty()
                .withPage(PageQuery.of(
                        ExampleSupport.intOption(args, "--limit", 20),
                        ExampleSupport.intOption(args, "--offset", 0)));

        String runId = ExampleSupport.option(args, "--run-id", null);
        if (runId != null) filter.withRunId(ExampleSupport.requiredLong(runId, "--run-id"));
        String q = ExampleSupport.option(args, "--q", null);
        if (q != null) filter.withQuery(q);
        String sales = ExampleSupport.option(args, "--sales-order", null);
        if (sales != null) filter.withSalesOrderNo(sales);
        String batch = ExampleSupport.option(args, "--batch", null);
        if (batch != null) filter.withProductionBatchCode(batch);
        String orderItem = ExampleSupport.option(args, "--production-order-item", null);
        if (orderItem != null) filter.withProductionOrderItemCode(orderItem);
        String track = ExampleSupport.option(args, "--track", null);
        if (track != null) filter.withTrackNumber(track);
        String statusName = ExampleSupport.option(args, "--status-name", null);
        if (statusName != null) filter.withStatusName(statusName);
        String status = ExampleSupport.option(args, "--status", null);
        if (status != null) filter.withCrawlStatus(parseStatus(status));
        String createdFrom = ExampleSupport.option(args, "--created-from", null);
        if (createdFrom != null) filter.withCreatedFrom(ExampleSupport.requiredLong(createdFrom, "--created-from"));
        String createdTo = ExampleSupport.option(args, "--created-to", null);
        if (createdTo != null) filter.withCreatedTo(ExampleSupport.requiredLong(createdTo, "--created-to"));
        String productionFrom = ExampleSupport.option(args, "--production-from", null);
        if (productionFrom != null) filter.withProductionFrom(ExampleSupport.requiredLong(productionFrom, "--production-from"));
        String productionTo = ExampleSupport.option(args, "--production-to", null);
        if (productionTo != null) filter.withProductionTo(ExampleSupport.requiredLong(productionTo, "--production-to"));

        ItemsListResponse items = crawler.listItems(filter);
        System.out.printf("items total=%d limit=%d offset=%d runId=%s%n",
                items.total(), items.limit(), items.offset(), ExampleSupport.nvl(items.runId()));
        items.items().forEach(ExampleSupport::printCrawlerItem);
    }

    private static void retryItem(CrawlerClient crawler, String[] args) {
        List<String> pos = ExampleSupport.positional(args);
        if (pos.size() < 2) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }
        ItemRetryResponse retry = crawler.retryItem(ExampleSupport.requiredLong(pos.get(1), "item-id"));
        System.out.printf("retry itemId=%s status=%s raw=%s%n",
                ExampleSupport.nvl(retry.itemId()), ExampleSupport.nvl(retry.status()), retry.raw());
    }

    private static CrawlStatus parseStatus(String value) {
        switch (value.toLowerCase()) {
            case "ok":
                return CrawlStatus.OK;
            case "failed":
                return CrawlStatus.FAILED;
            case "partial":
                return CrawlStatus.PARTIAL;
            case "product_image_failed":
                return CrawlStatus.PRODUCT_IMAGE_FAILED;
            case "shipping_label_failed":
                return CrawlStatus.SHIPPING_LABEL_FAILED;
            case "source_image_failed":
                return CrawlStatus.SOURCE_IMAGE_FAILED;
            case "production_image_failed":
                return CrawlStatus.PRODUCTION_IMAGE_FAILED;
            default:
                System.err.println("--status must be ok, failed, partial, product_image_failed, shipping_label_failed, source_image_failed, or production_image_failed");
                System.exit(ExampleSupport.ERR_USAGE);
                return null;
        }
    }

    private static void usage() {
        System.err.println("usage:");
        System.err.println("  CrawlerItemsExample list [--run-id ID] [--q TEXT] [--sales-order SO]");
        System.err.println("      [--batch CODE] [--production-order-item CODE] [--track NO]");
        System.err.println("      [--status-name NAME] [--status ok|failed|partial|product_image_failed|shipping_label_failed|source_image_failed|production_image_failed]");
        System.err.println("      [--created-from MS] [--created-to MS] [--production-from MS] [--production-to MS]");
        System.err.println("      [--limit N] [--offset N]");
        System.err.println("  CrawlerItemsExample retry-item <item-id>");
    }

    private CrawlerItemsExample() {}
}
