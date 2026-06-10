package io.podradar.examples;

import io.podradar.sdk.PodRadarClient;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.model.BatchUploadRequest;
import io.podradar.sdk.model.BatchUploadResponse;
import io.podradar.sdk.model.PageQuery;
import io.podradar.sdk.model.WriteJobItemsResponse;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * pod图像向量化系统 SDK:批量灌 URL + 写任务分页 + 失败重试。
 *
 * <pre>
 * export POD_RADAR_UPLOAD_URLS="https://cdn.example.com/a.jpg,https://cdn.example.com/b.jpg"
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.BatchUploadExample \
 *   -Dexec.args="--source snexdiy --source-id batch-20260607 --title 每日同步"
 * </pre>
 */
public final class BatchUploadExample {
    private static final int CHUNK = 50;

    public static void main(String[] args) throws InterruptedException {
        List<URI> urls = ExampleSupport.urlsFromArgsOrEnv(args, "POD_RADAR_UPLOAD_URLS", Collections.emptyList());
        if (urls.isEmpty()) {
            System.err.println("provide URLs as args or POD_RADAR_UPLOAD_URLS");
            System.exit(ExampleSupport.ERR_USAGE);
        }

        String source = ExampleSupport.option(args, "--source", "examples");
        String sourceId = ExampleSupport.option(args, "--source-id", null);
        String title = ExampleSupport.option(args, "--title", null);
        boolean retry = !ExampleSupport.hasFlag(args, "--no-retry");

        try (PodRadarClient client = ExampleSupport.podRadarBuilder()
                .requestTimeout(Duration.ofSeconds(90))
                .build()) {
            int total = 0, ok = 0, fail = 0, dup = 0;
            for (int i = 0; i < urls.size(); i += CHUNK) {
                List<URI> slice = urls.subList(i, Math.min(i + CHUNK, urls.size()));
                BatchUploadRequest req = BatchUploadRequest.fromUrls(slice).withSource(source);
                if (sourceId != null) req.withSourceId(sourceId);
                if (title != null) req.withTitle(title);

                BatchUploadResponse resp = client.uploadBatch(req);
                System.out.printf("[chunk %d/%d]%n", i / CHUNK + 1, (urls.size() + CHUNK - 1) / CHUNK);
                ExampleSupport.printBatchUpload(resp);
                total += resp.total();
                ok += resp.succeeded();
                fail += resp.failed();
                dup += resp.duplicate();

                printAllJobItems(client, resp.jobId());

                if (retry && resp.failed() > 0) {
                    BatchUploadResponse retryResp = client.retryFailedJob(resp.jobId());
                    System.out.println("retry result:");
                    ExampleSupport.printBatchUpload(retryResp);
                    printAllJobItems(client, retryResp.jobId());
                }
            }
            System.out.printf("DONE total=%d succeeded=%d failed=%d duplicate=%d%n", total, ok, fail, dup);
        } catch (PodRadarException e) {
            ExampleSupport.exitPodRadarException("batch upload failed", e);
        }
    }

    private static void printAllJobItems(PodRadarClient client, String jobId) throws InterruptedException {
        PageQuery page = PageQuery.of(50, 0);
        while (true) {
            WriteJobItemsResponse items = client.listJobItems(jobId, page);
            ExampleSupport.printWriteJobItems(items);
            if (items.offset() + items.items().size() >= items.total()) break;
            page = page.next();
            Thread.sleep(100);
        }
    }

    private BatchUploadExample() {}
}
