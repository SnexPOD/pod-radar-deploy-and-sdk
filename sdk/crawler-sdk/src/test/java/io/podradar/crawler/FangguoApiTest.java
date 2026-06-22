package io.podradar.crawler;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.podradar.crawler.model.FangguoAsset;
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
import io.podradar.crawler.model.FangguoSettings;
import io.podradar.crawler.model.FangguoSettingsResponse;
import io.podradar.crawler.model.FangguoStopResponse;
import io.podradar.crawler.model.FangguoSyncState;
import io.podradar.sdk.error.PodRadarConflictException;
import io.podradar.sdk.error.PodRadarNotFoundException;
import io.podradar.sdk.model.PageQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class FangguoApiTest {

    private WireMockServer server;
    private CrawlerClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        client = CrawlerClient.builder()
                .endpoint("http://localhost:" + server.port())
                .apiKey("test-key")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    // ───── settings + cursor ──────────────────────────────────────────

    @Test
    void getSettingsReturnsSettingsAndState() {
        server.stubFor(get(urlEqualTo("/api/v1/fangguo/settings"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"settings\":{\"sync_enabled\":true,\"sync_interval_minutes\":15," +
                        "\"sync_overlap_minutes\":5,\"cursor_start_at\":\"2026-01-01T00:00:00Z\"," +
                        "\"max_run_span_hours\":24}," +
                        "\"state\":{\"last_success_at\":\"2026-06-01T00:00:00Z\"," +
                        "\"last_started_at\":\"2026-06-01T00:00:00Z\",\"last_run_id\":42," +
                        "\"next_sync_at\":\"2026-06-01T00:15:00Z\"," +
                        "\"last_success_created_range\":{\"from\":1000,\"to\":2000}}}")));

        FangguoSettingsResponse resp = client.fangguo().getSettings();
        assertTrue(resp.settings().syncEnabled());
        assertEquals(15, resp.settings().syncIntervalMinutes());
        assertEquals(24, resp.settings().maxRunSpanHours());
        assertEquals(Long.valueOf(42L), resp.state().lastRunId());
        assertEquals("2026-06-01T00:15:00Z", resp.state().nextSyncAt());
        assertEquals(Long.valueOf(1000L), resp.state().lastSuccessCreatedFrom());
        assertEquals(Long.valueOf(2000L), resp.state().lastSuccessCreatedTo());
        server.verify(getRequestedFor(urlEqualTo("/api/v1/fangguo/settings"))
                .withHeader("X-API-Key", equalTo("test-key")));
    }

    @Test
    void updateSettingsSendsPutAndUnwrapsSettings() {
        server.stubFor(put(urlEqualTo("/api/v1/fangguo/settings"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"settings\":{\"sync_enabled\":false,\"sync_interval_minutes\":30," +
                        "\"sync_overlap_minutes\":5,\"cursor_start_at\":null,\"max_run_span_hours\":48}}")));

        FangguoSettings in = new FangguoSettings(false, 30, 5, null, 48);
        FangguoSettings out = client.fangguo().updateSettings(in);

        assertFalse(out.syncEnabled());
        assertEquals(30, out.syncIntervalMinutes());
        assertEquals(48, out.maxRunSpanHours());
        server.verify(putRequestedFor(urlEqualTo("/api/v1/fangguo/settings"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(matchingJsonPath("$.sync_enabled", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.max_run_span_hours", equalTo("48"))));
    }

    @Test
    void setCursorPostsAtAndUnwrapsState() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/cursor"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"state\":{\"last_success_at\":\"2026-06-04T08:03:00.000Z\",\"last_run_id\":417," +
                        "\"next_sync_at\":\"2026-06-04T08:18:00.000Z\"," +
                        "\"last_success_created_range\":{\"from\":1780473780000,\"to\":1780560180000}}}")));

        FangguoSyncState state = client.fangguo().setCursor("2026-06-04T16:03");
        assertEquals("2026-06-04T08:03:00.000Z", state.lastSuccessAt());
        assertEquals(Long.valueOf(417L), state.lastRunId());
        assertEquals(Long.valueOf(1780560180000L), state.lastSuccessCreatedTo());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/cursor"))
                .withRequestBody(equalToJson("{\"at\":\"2026-06-04T16:03\"}")));
    }

    @Test
    void setCursorConflictWhenRunning() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/cursor"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"error\":\"a run is in progress\",\"run_id\":99}")));

        PodRadarConflictException ex = assertThrows(PodRadarConflictException.class,
                () -> client.fangguo().setCursor(null));
        assertEquals(409, ex.statusCode());
    }

    // ───── runs ───────────────────────────────────────────────────────

    @Test
    void startRunIncrementalPostsBody() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run_id\":7,\"status\":\"running\"}")));

        FangguoRunResponse resp = client.fangguo().startRun(FangguoRunRequest.incremental().withDryRun(true));
        assertEquals(7L, resp.runId());
        assertEquals("running", resp.status());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/runs"))
                .withRequestBody(matchingJsonPath("$.mode", equalTo("incremental")))
                .withRequestBody(matchingJsonPath("$.dry_run", equalTo("true"))));
    }

    @Test
    void startRunBackfillSendsWindow() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run_id\":8,\"status\":\"running\"}")));

        client.fangguo().startRun(FangguoRunRequest.backfill(1000L, 2000L));

        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/runs"))
                .withRequestBody(matchingJsonPath("$.mode", equalTo("backfill")))
                .withRequestBody(matchingJsonPath("$.from", equalTo("1000")))
                .withRequestBody(matchingJsonPath("$.to", equalTo("2000"))));
    }

    @Test
    void startRunMapsConflictTo409() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/runs"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"error\":\"another run is in progress\",\"run_id\":3}")));

        PodRadarConflictException ex = assertThrows(PodRadarConflictException.class,
                () -> client.fangguo().startRun(FangguoRunRequest.incremental()));
        assertEquals(409, ex.statusCode());
        assertTrue(ex.getMessage().contains("another run is in progress"));
    }

    @Test
    void listRunsParsesLiveBlockAndPaging() {
        server.stubFor(get(urlPathEqualTo("/api/v1/fangguo/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"runs\":[" +
                        "{\"id\":11,\"run_id\":11,\"trigger\":\"manual\",\"mode\":\"incremental\"," +
                        "\"status\":\"running\",\"queued\":5,\"fetched\":4,\"failed\":1,\"duplicate\":0," +
                        "\"counts\":{},\"job_params\":{}," +
                        "\"live\":{\"assets\":{\"effect_image\":{\"pending\":1,\"fetching\":0,\"fetched\":4,\"failed\":1}}," +
                        "\"labels\":{\"pending\":2,\"converted\":3}}}]," +
                        "\"total\":1,\"limit\":20,\"offset\":0}")));

        FangguoRunsListResponse resp = client.fangguo().listRuns(PageQuery.of(20, 0));
        assertEquals(1, resp.total());
        assertEquals(1, resp.runs().size());
        FangguoRun run = resp.runs().get(0);
        assertEquals(11L, run.id());
        assertEquals(11L, run.runId());
        assertEquals(4, run.live().effectImage().fetched());
        assertEquals(1, run.live().effectImage().failed());
        assertEquals(Integer.valueOf(2), run.live().labels().get("pending"));
        // production_image absent → assetOrEmpty returns zeroed counts
        assertEquals(0, run.live().productionImage().fetched());

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/fangguo/runs"))
                .withQueryParam("limit", equalTo("20"))
                .withQueryParam("offset", equalTo("0")));
    }

    @Test
    void getRunUnwrapsRun() {
        server.stubFor(get(urlEqualTo("/api/v1/fangguo/runs/9"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run\":{\"id\":9,\"run_id\":9,\"status\":\"done\",\"trigger\":\"scheduled\"," +
                        "\"mode\":\"incremental\",\"queued\":3,\"fetched\":3,\"failed\":0,\"duplicate\":0," +
                        "\"counts\":{},\"job_params\":{},\"live\":{\"assets\":{},\"labels\":{}}}}")));

        FangguoRun run = client.fangguo().getRun(9);
        assertEquals(9L, run.id());
        assertEquals("done", run.status());
        assertEquals(3, run.fetched());
    }

    @Test
    void getRunMissingMapsTo404() {
        server.stubFor(get(urlEqualTo("/api/v1/fangguo/runs/404"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"run not found\"}")));

        PodRadarNotFoundException ex = assertThrows(PodRadarNotFoundException.class,
                () -> client.fangguo().getRun(404));
        assertEquals(404, ex.statusCode());
    }

    @Test
    void listRunItemsParsesAssetsAndLabels() {
        server.stubFor(get(urlPathEqualTo("/api/v1/fangguo/runs/9/items"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run\":{\"id\":9,\"run_id\":9,\"status\":\"done\",\"counts\":{},\"job_params\":{}," +
                        "\"live\":{\"assets\":{},\"labels\":{}}}," +
                        "\"items\":[{\"id\":1,\"order_id\":100,\"tid\":\"T-1\",\"unit_key\":\"U-1\"," +
                        "\"barcode\":\"B-1\",\"oid\":\"O-1\",\"sku_id\":\"S-1\",\"cover_status\":\"ok\"," +
                        "\"unit_idx\":1,\"unit_total\":2," +
                        "\"order\":{\"ship_status\":\"shipped\",\"store_name\":\"shop\",\"has_package\":\"1\"}," +
                        "\"assets\":[{\"id\":5,\"asset_kind\":\"effect_image\",\"status\":\"fetched\"," +
                        "\"width\":800,\"height\":600,\"thumb\":\"http://x/t\",\"full\":\"http://x/t\"}]," +
                        "\"labels\":[{\"id\":6,\"package_no\":\"P-1\",\"status\":\"converted\"," +
                        "\"track_numbers\":[\"TN1\",\"TN2\"],\"page_count\":2,\"pages\":[\"http://x/p1\"]}]}]," +
                        "\"total\":1,\"limit\":50,\"offset\":0,\"detail_source\":\"run_items\"}")));

        FangguoItemsResponse resp = client.fangguo().listRunItems(9, PageQuery.of(50, 0));
        assertEquals(1, resp.total());
        assertEquals("run_items", resp.detailSource());
        assertNotNull(resp.run());
        assertEquals(9L, resp.run().id());

        FangguoItem item = resp.items().get(0);
        assertEquals(1L, item.id());
        assertEquals(100L, item.orderId());
        assertEquals("O-1", item.oid());
        assertEquals(Integer.valueOf(1), item.unitIdx());
        assertEquals("shipped", item.order().shipStatus());
        assertEquals("1", item.order().hasPackage());

        FangguoAsset asset = item.assets().get(0);
        assertEquals(Long.valueOf(5L), asset.id());
        assertEquals("effect_image", asset.assetKind());
        assertEquals(Integer.valueOf(800), asset.width());

        FangguoLabel label = item.labels().get(0);
        assertEquals(Long.valueOf(6L), label.id());
        assertEquals(2, label.trackNumbers().size());
        assertEquals("TN1", label.trackNumbers().get(0));
        assertEquals(Integer.valueOf(2), label.pageCount());

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/fangguo/runs/9/items"))
                .withQueryParam("limit", equalTo("50")));
    }

    @Test
    void stopRunSendsPauseAuto() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/runs/446/stop"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"stopped\",\"run_id\":446,\"pause_auto\":true," +
                        "\"stopped_assets\":12,\"stopped_labels\":3}")));

        FangguoStopResponse resp = client.fangguo().stopRun(446, true);
        assertEquals("stopped", resp.status());
        assertEquals(446L, resp.runId());
        assertTrue(resp.pauseAuto());
        assertEquals(12, resp.stoppedAssets());
        assertEquals(3, resp.stoppedLabels());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/runs/446/stop"))
                .withRequestBody(matchingJsonPath("$.pause_auto", equalTo("true"))));
    }

    @Test
    void stopRunNotRunningMapsTo409() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/runs/446/stop"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"status\":\"not_running\",\"run_id\":446,\"run_status\":\"done\"}")));

        PodRadarConflictException ex = assertThrows(PodRadarConflictException.class,
                () -> client.fangguo().stopRun(446));
        assertEquals(409, ex.statusCode());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/runs/446/stop"))
                .withRequestBody(matchingJsonPath("$.pause_auto", equalTo("false"))));
    }

    @Test
    void retryFailedRunReturnsCounts() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/runs/42/retry-failed"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"run_id\":42,\"requeued_assets\":5," +
                        "\"requeued_labels\":2,\"item_count\":4}")));

        FangguoRetryResponse resp = client.fangguo().retryFailedRun(42);
        assertEquals("queued", resp.status());
        assertEquals(Long.valueOf(42L), resp.runId());
        assertEquals(5, resp.requeuedAssets());
        assertEquals(2, resp.requeuedLabels());
        assertEquals(4, resp.itemCount());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/runs/42/retry-failed"))
                .withRequestBody(equalTo("{}")));
    }

    @Test
    void retryFailedAllAcceptsNullRunId() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/retry-failed"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"empty\",\"run_id\":null,\"requeued_assets\":0," +
                        "\"requeued_labels\":0,\"item_count\":0}")));

        FangguoRetryResponse resp = client.fangguo().retryFailedAll();
        assertEquals("empty", resp.status());
        assertNull(resp.runId());
        assertEquals(0, resp.itemCount());
    }

    // ───── items ──────────────────────────────────────────────────────

    @Test
    void listItemsBuildsQueryFromFilter() {
        server.stubFor(get(urlPathEqualTo("/api/v1/fangguo/items"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run\":null,\"items\":[{\"id\":901,\"order_id\":77,\"tid\":\"T1\"," +
                        "\"source_created_at\":\"2026-06-10T00:00:00.000Z\",\"order\":{}," +
                        "\"assets\":[],\"labels\":[]}],\"total\":1,\"limit\":10,\"offset\":20," +
                        "\"detail_source\":\"all\"}")));

        FangguoItemsFilter filter = FangguoItemsFilter.empty()
                .withRunId(372)
                .withQuery("abc")
                .withShipStatus("shipped")
                .withCrawlStatus(FangguoCrawlStatus.FAILED)
                .withCreatedRange(1781136000000L, 1781222399999L)
                .withPage(PageQuery.of(10, 20));

        FangguoItemsResponse resp = client.fangguo().listItems(filter);
        assertEquals(1, resp.total());
        assertNull(resp.run());
        assertEquals("all", resp.detailSource());
        assertEquals("2026-06-10T00:00:00.000Z", resp.items().get(0).sourceCreatedAt());

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/fangguo/items"))
                .withQueryParam("run_id", equalTo("372"))
                .withQueryParam("q", equalTo("abc"))
                .withQueryParam("ship_status", equalTo("shipped"))
                .withQueryParam("crawl_status", equalTo("failed"))
                .withQueryParam("created_from", equalTo("1781136000000"))
                .withQueryParam("created_to", equalTo("1781222399999"))
                .withQueryParam("limit", equalTo("10"))
                .withQueryParam("offset", equalTo("20")));
    }

    @Test
    void listItemsUnknownRunMapsTo404() {
        server.stubFor(get(urlPathEqualTo("/api/v1/fangguo/items"))
                .willReturn(aResponse().withStatus(404).withBody("{\"error\":\"run not found\"}")));

        PodRadarNotFoundException ex = assertThrows(PodRadarNotFoundException.class,
                () -> client.fangguo().listItems(FangguoItemsFilter.empty().withRunId(999)));
        assertEquals(404, ex.statusCode());
    }

    @Test
    void retryItemPostsEmptyBody() {
        server.stubFor(post(urlEqualTo("/api/v1/fangguo/items/123/retry"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"run_id\":778,\"order_unit_id\":123,\"requeued_assets\":1," +
                        "\"requeued_labels\":0}")));

        FangguoItemRetryResponse resp = client.fangguo().retryItem(123);
        assertEquals("queued", resp.status());
        assertEquals(Long.valueOf(778L), resp.runId());
        assertEquals(123L, resp.orderUnitId());
        assertEquals(1, resp.requeuedAssets());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/fangguo/items/123/retry"))
                .withRequestBody(equalTo("{}")));
    }
}
