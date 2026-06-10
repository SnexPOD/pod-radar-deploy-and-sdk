package io.podradar.examples;

import io.podradar.sdk.PodRadarClient;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.model.BatchSearchAsyncResponse;
import io.podradar.sdk.model.BatchSearchRequest;
import io.podradar.sdk.model.ImageRef;
import io.podradar.sdk.model.PageQuery;
import io.podradar.sdk.model.SearchJobItemsResponse;
import io.podradar.sdk.model.SearchJobStatus;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * pod图像向量化系统 SDK:批量异步搜索 URL。
 *
 * <pre>
 * export POD_RADAR_SEARCH_URLS="https://cdn.example.com/q1.jpg https://cdn.example.com/q2.jpg"
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.BatchSearchExample \
 *   -Dexec.args="--k 12 --min-score 0.82"
 * </pre>
 */
public final class BatchSearchExample {

    public static void main(String[] args) throws InterruptedException {
        List<URI> urls = ExampleSupport.urlsFromArgsOrEnv(args, "POD_RADAR_SEARCH_URLS", Collections.emptyList());
        if (urls.isEmpty()) {
            System.err.println("provide URLs as args or POD_RADAR_SEARCH_URLS");
            System.exit(ExampleSupport.ERR_USAGE);
        }

        int k = ExampleSupport.intOption(args, "--k", 24);
        double minScore = ExampleSupport.doubleOption(args, "--min-score", 0.0);

        try (PodRadarClient client = ExampleSupport.podRadarBuilder()
                .requestTimeout(Duration.ofSeconds(60))
                .build()) {
            BatchSearchAsyncResponse created = client.searchBatch(BatchSearchRequest.fromUrls(urls)
                    .withK(k)
                    .withMinScore(minScore));
            System.out.printf("searchJobId=%d total=%d enqueued=%d status=%s statusUrl=%s%n",
                    created.searchJobId(), created.total(), created.enqueued(),
                    created.status(), created.statusUrl());

            SearchJobStatus status;
            while (true) {
                status = client.getSearchJob(created.searchJobId());
                System.out.printf("job status=%s completed=%d failed=%d total=%d model=%s%n",
                        status.status(), status.completed(), status.failed(), status.total(), status.model());
                if (ExampleSupport.terminal(status.status())) break;
                Thread.sleep(Duration.ofSeconds(3).toMillis());
            }

            PageQuery page = PageQuery.of(50, 0);
            while (true) {
                SearchJobItemsResponse items = client.listSearchJobItems(created.searchJobId(), page);
                printItems(items);
                if (items.offset() + items.items().size() >= items.total()) break;
                page = page.next();
            }
        } catch (PodRadarException e) {
            ExampleSupport.exitPodRadarException("batch search failed", e);
        }
    }

    private static void printItems(SearchJobItemsResponse items) {
        System.out.printf("items total=%d limit=%d offset=%d%n", items.total(), items.limit(), items.offset());
        for (SearchJobItemsResponse.Item item : items.items()) {
            int hits = item.hits() == null ? -1 : item.hits().size();
            System.out.printf("  #%d id=%d status=%s attempts=%d hits=%s url=%s error=%s%n",
                    item.itemIndex(), item.id(), item.status(), item.attempts(),
                    hits < 0 ? "pending" : String.valueOf(hits), item.imageUrl(), ExampleSupport.nvl(item.lastError()));
            if (item.hits() != null) {
                for (ImageRef hit : item.hits()) {
                    System.out.printf("      imageId=%d score=%.4f title=%s full=%s%n",
                            hit.imageId(), hit.score(), ExampleSupport.nvl(hit.title()), ExampleSupport.nvl(hit.full()));
                }
            }
        }
    }

    private BatchSearchExample() {}
}
