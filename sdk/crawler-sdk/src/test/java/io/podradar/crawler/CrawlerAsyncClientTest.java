package io.podradar.crawler;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.podradar.crawler.model.RetryFailedKind;
import io.podradar.crawler.model.RetryFailedKindRequest;
import io.podradar.crawler.model.RetryFailedKindResponse;
import io.podradar.crawler.model.RescanResponse;
import io.podradar.crawler.model.RunRequest;
import io.podradar.crawler.model.RunResponse;
import io.podradar.crawler.model.SettingsResponse;
import io.podradar.crawler.model.StopRetryResponse;
import io.podradar.sdk.error.PodRadarConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class CrawlerAsyncClientTest {

    private WireMockServer server;
    private CrawlerAsyncClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        client = CrawlerAsyncClient.builder()
                .endpoint("http://localhost:" + server.port())
                .apiKey("test-key")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    void getSettingsAsyncResolves() throws Exception {
        server.stubFor(get(urlEqualTo("/api/v1/hihumbird/settings"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"settings\":{\"sync_enabled\":true,\"sync_interval_minutes\":15," +
                        "\"sync_overlap_minutes\":5,\"cursor_start_at\":\"2026-01-01T00:00:00Z\"," +
                        "\"max_run_span_hours\":24,\"rescan_pending_enabled\":true," +
                        "\"rescan_pending_interval_minutes\":60,\"rescan_pending_max_age_days\":3," +
                        "\"rescan_missing_batch_enabled\":true," +
                        "\"rescan_missing_batch_interval_minutes\":90," +
                        "\"rescan_missing_batch_max_age_days\":30}," +
                        "\"state\":{}}")));

        SettingsResponse resp = client.getSettings().get();
        assertTrue(resp.settings().syncEnabled());
        assertEquals(90, resp.settings().rescanMissingBatchIntervalMinutes());
        assertNull(resp.state().lastRunId());
    }

    @Test
    void startRunAsyncResolves() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run_id\":7,\"status\":\"running\"}")));

        RunResponse resp = client.startRun(RunRequest.incremental()).get();
        assertEquals(7L, resp.runId());
    }

    @Test
    void startRunAsyncFailsOn409() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"error\":\"another run is in progress\"}")));

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> client.startRun(RunRequest.incremental()).get());
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof PodRadarConflictException,
                "expected PodRadarConflictException, got " + cause);
    }

    @Test
    void deleteKeyAsyncCompletes() throws Exception {
        server.stubFor(delete(urlEqualTo("/api/v1/keys/3"))
                .willReturn(aResponse().withStatus(204)));
        client.deleteKey(3).get();
        server.verify(deleteRequestedFor(urlEqualTo("/api/v1/keys/3")));
    }

    @Test
    void retryFailedKindAsyncResolves() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/retry-failed"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"run_id\":600,\"kind\":\"production_image\"," +
                        "\"queued\":42,\"skipped_no_url\":0,\"reharvest\":false}")));

        RetryFailedKindResponse resp =
                client.retryFailedKind(RetryFailedKindRequest.of(RetryFailedKind.PRODUCTION_IMAGE)).get();
        assertTrue(resp.isQueued());
        assertEquals(42, resp.queued());
        assertFalse(resp.reharvest());
    }

    @Test
    void rescanMissingBatchesAsyncResolves() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/rescan-missing-batches"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"run_id\":2051,\"item_count\":975}")));

        RescanResponse resp = client.rescanMissingBatches().get();
        assertTrue(resp.isQueued());
        assertEquals(2051L, resp.runId());
        assertEquals(975, resp.itemCount().getAsInt());
    }

    @Test
    void stopRetryAsyncResolves() throws Exception {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs/9/stop-retry"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"stopped\",\"run_id\":9,\"stopped_assets\":0,\"stopped_labels\":0}")));

        StopRetryResponse resp = client.stopRetry(9).get();
        assertEquals("stopped", resp.status());
        assertEquals(9L, resp.runId());
    }
}
