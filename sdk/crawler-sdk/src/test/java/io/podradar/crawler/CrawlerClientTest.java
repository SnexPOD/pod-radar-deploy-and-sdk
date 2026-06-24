package io.podradar.crawler;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.podradar.crawler.model.CreateKeyResponse;
import io.podradar.crawler.model.CrawlStatus;
import io.podradar.crawler.model.AccountTestResult;
import io.podradar.crawler.model.CrawlerAccount;
import io.podradar.crawler.model.CrawlerKey;
import io.podradar.crawler.model.CreateAccountRequest;
import io.podradar.crawler.model.TestAccountRequest;
import io.podradar.crawler.model.HihumbirdSettings;
import io.podradar.crawler.model.ItemRetryResponse;
import io.podradar.crawler.model.ItemsFilter;
import io.podradar.crawler.model.ItemsListResponse;
import io.podradar.crawler.model.HihumbirdSyncState;
import io.podradar.crawler.model.MeResponse;
import io.podradar.crawler.model.RescanResponse;
import io.podradar.crawler.model.RetryFailedKind;
import io.podradar.crawler.model.RetryFailedKindRequest;
import io.podradar.crawler.model.RetryFailedKindResponse;
import io.podradar.crawler.model.RetryRunResponse;
import io.podradar.crawler.model.StopRetryResponse;
import io.podradar.crawler.model.RunRequest;
import io.podradar.crawler.model.RunResponse;
import io.podradar.crawler.model.RunsListResponse;
import io.podradar.crawler.model.SettingsResponse;
import io.podradar.sdk.error.PodRadarConflictException;
import io.podradar.sdk.model.PageQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class CrawlerClientTest {

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

    // ───── settings ───────────────────────────────────────────────────

    @Test
    void getSettingsReturnsSettingsAndState() {
        server.stubFor(get(urlEqualTo("/api/v1/hihumbird/settings"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"settings\":{\"sync_enabled\":true,\"sync_interval_minutes\":15," +
                        "\"sync_overlap_minutes\":5,\"cursor_start_at\":\"2026-01-01T00:00:00Z\"," +
                        "\"max_run_span_hours\":24,\"rescan_pending_enabled\":true," +
                        "\"rescan_pending_interval_minutes\":60,\"rescan_pending_max_age_days\":3," +
                        "\"rescan_missing_batch_enabled\":true," +
                        "\"rescan_missing_batch_interval_minutes\":90," +
                        "\"rescan_missing_batch_max_age_days\":30}," +
                        "\"state\":{\"last_success_at\":\"2026-06-01T00:00:00Z\"," +
                        "\"last_started_at\":\"2026-06-01T00:00:00Z\",\"last_run_id\":42," +
                        "\"last_success_created_range\":{\"from\":1000,\"to\":2000}}}")));

        SettingsResponse resp = client.getSettings();
        assertTrue(resp.settings().syncEnabled());
        assertEquals(15, resp.settings().syncIntervalMinutes());
        assertTrue(resp.settings().rescanMissingBatchEnabled());
        assertEquals(90, resp.settings().rescanMissingBatchIntervalMinutes());
        assertEquals(30, resp.settings().rescanMissingBatchMaxAgeDays());
        assertEquals(Long.valueOf(42L), resp.state().lastRunId());
        assertEquals(Long.valueOf(1000L), resp.state().lastSuccessCreatedFrom());
        assertEquals(Long.valueOf(2000L), resp.state().lastSuccessCreatedTo());
    }

    @Test
    void updateSettingsSendsPutAndUnwrapsSettings() {
        server.stubFor(put(urlEqualTo("/api/v1/hihumbird/settings"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"settings\":{\"sync_enabled\":false,\"sync_interval_minutes\":30," +
                        "\"sync_overlap_minutes\":5,\"cursor_start_at\":\"2026-01-01T00:00:00Z\"," +
                        "\"max_run_span_hours\":24,\"rescan_pending_enabled\":false," +
                        "\"rescan_pending_interval_minutes\":60,\"rescan_pending_max_age_days\":3," +
                        "\"rescan_missing_batch_enabled\":true," +
                        "\"rescan_missing_batch_interval_minutes\":90," +
                        "\"rescan_missing_batch_max_age_days\":30}}")));

        HihumbirdSettings in = new HihumbirdSettings(
                false, 30, 5, "2026-01-01T00:00:00Z", 24, false, 60, 3, true, 90, 30);
        HihumbirdSettings out = client.updateSettings(in);

        assertFalse(out.syncEnabled());
        assertEquals(30, out.syncIntervalMinutes());
        server.verify(putRequestedFor(urlEqualTo("/api/v1/hihumbird/settings"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(matchingJsonPath("$.sync_enabled", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.sync_interval_minutes", equalTo("30")))
                .withRequestBody(matchingJsonPath("$.rescan_missing_batch_interval_minutes", equalTo("90"))));
    }

    // ───── runs ───────────────────────────────────────────────────────

    @Test
    void startRunIncrementalPostsBody() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run_id\":7,\"status\":\"running\",\"item_count\":3}")));

        RunResponse resp = client.startRun(RunRequest.incremental().withDryRun(true));
        assertEquals(7L, resp.runId());
        assertEquals("running", resp.status());
        assertTrue(resp.itemCount().isPresent());
        assertEquals(3, resp.itemCount().getAsInt());

        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/runs"))
                .withRequestBody(matchingJsonPath("$.mode", equalTo("incremental")))
                .withRequestBody(matchingJsonPath("$.dry_run", equalTo("true"))));
    }

    @Test
    void startRunBackfillSendsWindow() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run_id\":8,\"status\":\"running\"}")));

        client.startRun(RunRequest.backfill(1000L, 2000L).withBatchCode("B-2026"));

        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/runs"))
                .withRequestBody(matchingJsonPath("$.mode", equalTo("backfill")))
                .withRequestBody(matchingJsonPath("$.from", equalTo("1000")))
                .withRequestBody(matchingJsonPath("$.to", equalTo("2000")))
                .withRequestBody(matchingJsonPath("$.batch_code", equalTo("B-2026"))));
    }

    @Test
    void startRunMapsConflictTo409() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs"))
                .willReturn(aResponse().withStatus(409).withBody(
                        "{\"error\":\"another run is in progress\",\"request_id\":\"r-1\"}")));

        PodRadarConflictException ex = assertThrows(PodRadarConflictException.class,
                () -> client.startRun(RunRequest.incremental()));
        assertEquals(409, ex.statusCode());
        assertTrue(ex.getMessage().contains("another run is in progress"));
    }

    @Test
    void listRunsPassesPaging() {
        server.stubFor(get(urlPathEqualTo("/api/v1/hihumbird/runs"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"runs\":[" +
                        "{\"id\":11,\"trigger\":\"manual\",\"mode\":\"incremental\"," +
                        "\"status\":\"succeeded\",\"queued\":5,\"fetched\":5,\"failed\":0,\"duplicate\":0," +
                        "\"counts\":{},\"failures\":[]}]," +
                        "\"total\":1,\"limit\":20,\"offset\":0}")));

        RunsListResponse resp = client.listRuns(PageQuery.of(20, 0));
        assertEquals(1, resp.total());
        assertEquals(1, resp.runs().size());
        assertEquals(11L, resp.runs().get(0).id());

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/hihumbird/runs"))
                .withQueryParam("limit", equalTo("20"))
                .withQueryParam("offset", equalTo("0")));
    }

    @Test
    void retryFailedRunReturnsItemCount() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs/42/retry-failed"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"run_id\":42,\"status\":\"running\",\"item_count\":2}")));

        RetryRunResponse resp = client.retryFailedRun(42);
        assertEquals(42L, resp.runId());
        assertTrue(resp.itemCount().isPresent());
        assertEquals(2, resp.itemCount().getAsInt());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/runs/42/retry-failed"))
                .withRequestBody(equalTo("{}")));
    }

    @Test
    void rescanPendingLabelsParsesResponse() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/rescan-pending-labels"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"queued\",\"run_id\":17,\"item_count\":4}")));

        RescanResponse resp = client.rescanPendingLabels();
        assertEquals("queued", resp.status());
        assertEquals(17L, resp.runId());
        assertEquals(4, resp.itemCount().getAsInt());
    }

    @Test
    void rescanPendingLabelsAcceptsMissingItemCount() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/rescan-pending-labels"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"queued\",\"run_id\":18}")));

        RescanResponse resp = client.rescanPendingLabels();
        assertEquals(18L, resp.runId());
        assertFalse(resp.itemCount().isPresent());
    }

    @Test
    void rescanMissingBatchesQueuesWhenBusy() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/rescan-missing-batches"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"run_id\":2051,\"item_count\":975,\"account_id\":null," +
                        "\"message\":\"hihumbird sync already running; missing-batch rescan queued\"}")));

        RescanResponse resp = client.rescanMissingBatches();
        assertTrue(resp.isQueued());
        assertEquals(2051L, resp.runId());
        assertEquals(975, resp.itemCount().getAsInt());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/rescan-missing-batches"))
                .withRequestBody(equalTo("{}")));
    }

    @Test
    void rescanMissingBatchesCanBeAccountScoped() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/rescan-missing-batches"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"running\",\"run_id\":2064,\"item_count\":12}")));

        RescanResponse resp = client.rescanMissingBatches(9L);
        assertTrue(resp.isRunning());
        assertEquals(2064L, resp.runId());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/rescan-missing-batches"))
                .withRequestBody(equalToJson("{\"account_id\":9}")));
    }

    // ───── kind-scoped retry / stop / cursor ──────────────────────────

    @Test
    void retryFailedKindProductImageChunked() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/retry-failed"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"run_id\":500,\"kind\":\"product_image\"," +
                        "\"queued\":1500,\"skipped_no_url\":0,\"reharvest\":true," +
                        "\"batches\":2,\"run_ids\":[500,501]}")));

        ItemsFilter filter = ItemsFilter.empty()
                .withRunId(372)
                .withCreatedRange(1781136000000L, 1781222399999L)
                .withProductionFrom(1781222400000L);
        RetryFailedKindResponse resp = client.retryFailedKind(
                RetryFailedKindRequest.of(RetryFailedKind.PRODUCT_IMAGE).withFilter(filter));

        assertTrue(resp.isQueued());
        assertEquals("product_image", resp.kind());
        assertEquals(1500, resp.queued());
        assertTrue(resp.reharvest());
        assertEquals(2, resp.batches().getAsInt());
        assertEquals(java.util.Arrays.asList(500L, 501L), resp.runIds());
        assertEquals(500L, resp.runId().getAsLong());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/retry-failed"))
                .withRequestBody(equalToJson("{\"kind\":\"product_image\",\"run_id\":372,"
                        + "\"created_from\":1781136000000,\"created_to\":1781222399999,"
                        + "\"production_from\":1781222400000}")));
    }

    @Test
    void retryFailedKindEmpty() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/retry-failed"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"empty\",\"run_id\":null,\"kind\":\"label\"," +
                        "\"queued\":0,\"skipped_no_url\":0,\"reharvest\":false}")));

        RetryFailedKindResponse resp = client.retryFailedKind(RetryFailedKindRequest.of(RetryFailedKind.LABEL));
        assertTrue(resp.isEmpty());
        assertEquals(0, resp.queued());
        assertFalse(resp.runId().isPresent());
        assertTrue(resp.runIds().isEmpty());
        assertFalse(resp.batches().isPresent());
    }

    @Test
    void stopRetryParsesResponse() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/runs/446/stop-retry"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"stopped\",\"run_id\":446,\"stopped_assets\":12,\"stopped_labels\":3}")));

        StopRetryResponse resp = client.stopRetry(446);
        assertEquals("stopped", resp.status());
        assertEquals(446L, resp.runId());
        assertEquals(12, resp.stoppedAssets());
        assertEquals(3, resp.stoppedLabels());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/runs/446/stop-retry"))
                .withRequestBody(equalTo("{}")));
    }

    @Test
    void setCursorPostsAtAndUnwrapsState() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/cursor"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"state\":{\"last_success_at\":\"2026-06-04T08:03:00.000Z\",\"last_run_id\":417," +
                        "\"last_success_created_range\":{\"from\":1780473780000,\"to\":1780560180000}}}")));

        HihumbirdSyncState state = client.setCursor("2026-06-04T16:03");
        assertEquals("2026-06-04T08:03:00.000Z", state.lastSuccessAt());
        assertEquals(417L, state.lastRunId());
        assertEquals(1780560180000L, state.lastSuccessCreatedTo());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/cursor"))
                .withRequestBody(equalToJson("{\"at\":\"2026-06-04T16:03\"}")));
    }

    @Test
    void setCursorNullClearsCursor() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/cursor"))
                .willReturn(aResponse().withStatus(200).withBody("{\"state\":{}}")));

        client.setCursor(null);
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/cursor"))
                .withRequestBody(equalToJson("{\"at\":null}")));
    }

    @Test
    void listRunItemsPagesItems() {
        server.stubFor(get(urlPathEqualTo("/api/v1/hihumbird/runs/9/items"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"items\":[" +
                        "{\"id\":1,\"asset_kind\":\"label\",\"sales_order_no\":\"SO-1\"," +
                        "\"status\":\"fetched\"}]," +
                        "\"total\":1,\"limit\":50,\"offset\":0,\"run_id\":9}")));

        ItemsListResponse resp = client.listRunItems(9, PageQuery.of(50, 0));
        assertEquals(1, resp.total());
        assertEquals(Long.valueOf(9L), resp.runId());
        assertEquals(1L, resp.items().get(0).id());
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/hihumbird/runs/9/items"))
                .withQueryParam("limit", equalTo("50")));
    }

    // ───── items ──────────────────────────────────────────────────────

    @Test
    void listItemsBuildsQueryFromFilter() {
        server.stubFor(get(urlPathEqualTo("/api/v1/hihumbird/items"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"items\":[],\"total\":0,\"limit\":10,\"offset\":0}")));

        ItemsFilter filter = ItemsFilter.empty()
                .withSalesOrderNo("SO-7")
                .withCrawlStatus(CrawlStatus.FAILED)
                .withCreatedRange(1781136000000L, 1781222399999L)
                .withProductionRange(1781222400000L, 1781308799999L)
                .withPage(PageQuery.of(10, 20));

        ItemsListResponse resp = client.listItems(filter);
        assertEquals(0, resp.total());

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/hihumbird/items"))
                .withQueryParam("sales_order_no", equalTo("SO-7"))
                .withQueryParam("crawl_status", equalTo("failed"))
                .withQueryParam("created_from", equalTo("1781136000000"))
                .withQueryParam("created_to", equalTo("1781222399999"))
                .withQueryParam("production_from", equalTo("1781222400000"))
                .withQueryParam("production_to", equalTo("1781308799999"))
                .withQueryParam("limit", equalTo("10"))
                .withQueryParam("offset", equalTo("20")));
    }

    @Test
    void retryItemPostsEmptyBody() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/items/123/retry"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"status\":\"queued\",\"item_id\":123}")));

        ItemRetryResponse resp = client.retryItem(123);
        assertEquals("queued", resp.status());
        assertEquals(Long.valueOf(123L), resp.itemId());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/items/123/retry"))
                .withRequestBody(equalTo("{}")));
    }

    @Test
    void retryItemForcePostsForceTrue() {
        server.stubFor(post(urlEqualTo("/api/v1/hihumbird/items/123/retry"))
                .willReturn(aResponse().withStatus(202).withBody(
                        "{\"status\":\"queued\",\"item_id\":123}")));

        ItemRetryResponse resp = client.retryItem(123, true);
        assertEquals("queued", resp.status());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/hihumbird/items/123/retry"))
                .withRequestBody(equalToJson("{\"force\":true}")));
    }

    // ───── keys ───────────────────────────────────────────────────────

    @Test
    void listKeysReturnsArray() {
        server.stubFor(get(urlEqualTo("/api/v1/keys"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"keys\":[" +
                        "{\"id\":1,\"name\":\"ops\",\"prefix\":\"pod-radar_AAAA\"," +
                        "\"created_at\":\"2026-01-01T00:00:00Z\",\"use_count\":7}]}")));

        List<CrawlerKey> keys = client.listKeys();
        assertEquals(1, keys.size());
        assertEquals("ops", keys.get(0).name());
        assertEquals(7L, keys.get(0).useCount());
    }

    @Test
    void createKeyReturnsPlaintextOnce() {
        server.stubFor(post(urlEqualTo("/api/v1/keys"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"id\":2,\"name\":\"ci\",\"prefix\":\"pod-radar_BBBB\"," +
                        "\"created_at\":\"2026-06-01T00:00:00Z\",\"use_count\":0," +
                        "\"plaintext\":\"pod-radar_FULLTOKENVALUE\"}")));

        CreateKeyResponse resp = client.createKey("ci");
        assertEquals(2L, resp.id());
        assertEquals("ci", resp.name());
        assertEquals("pod-radar_FULLTOKENVALUE", resp.plaintext());

        server.verify(postRequestedFor(urlEqualTo("/api/v1/keys"))
                .withHeader("Content-Type", matching("application/json.*"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("ci"))));
    }

    @Test
    void revokeKeyPostsEmpty() {
        server.stubFor(post(urlEqualTo("/api/v1/keys/5/revoke"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));
        client.revokeKey(5);
        server.verify(postRequestedFor(urlEqualTo("/api/v1/keys/5/revoke"))
                .withRequestBody(equalTo("{}")));
    }

    @Test
    void deleteKeySendsDelete() {
        server.stubFor(delete(urlEqualTo("/api/v1/keys/6"))
                .willReturn(aResponse().withStatus(204)));
        client.deleteKey(6);
        server.verify(deleteRequestedFor(urlEqualTo("/api/v1/keys/6")));
    }

    // ───── accounts ─────────────────────────────────────────────────────

    @Test
    void listAccountsFiltersBySystem() {
        server.stubFor(get(urlEqualTo("/api/v1/accounts?system=fangguo"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"accounts\":[{\"id\":3,\"system\":\"fangguo\",\"name\":\"默认(.env)\"," +
                        "\"username\":\"19397991899\",\"enabled\":true,\"created_at\":\"2026-06-22T00:00:00Z\"}]}")));

        List<CrawlerAccount> accts = client.listAccounts("fangguo");
        assertEquals(1, accts.size());
        assertEquals("fangguo", accts.get(0).system());
        assertEquals("19397991899", accts.get(0).username());
        assertTrue(accts.get(0).enabled());
    }

    @Test
    void createAccountPostsCredentials() {
        server.stubFor(post(urlEqualTo("/api/v1/accounts"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"id\":4,\"system\":\"fangguo\",\"name\":\"A店\",\"username\":\"u1\"," +
                        "\"enabled\":true,\"created_at\":\"2026-06-22T00:00:00Z\"}")));

        CrawlerAccount a = client.createAccount(
                new CreateAccountRequest("fangguo", "A店", "u1", "p1").withTenantId("T9"));
        assertEquals(4L, a.id());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/accounts"))
                .withRequestBody(matchingJsonPath("$.username", equalTo("u1")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("p1")))
                .withRequestBody(matchingJsonPath("$.tenant_id", equalTo("T9"))));
    }

    @Test
    void setAccountEnabledPutsEnabledFlag() {
        server.stubFor(put(urlEqualTo("/api/v1/accounts/4"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"id\":4,\"system\":\"fangguo\",\"name\":\"A店\",\"username\":\"u1\",\"enabled\":false}")));

        CrawlerAccount a = client.setAccountEnabled(4, false);
        assertFalse(a.enabled());
        server.verify(putRequestedFor(urlEqualTo("/api/v1/accounts/4"))
                .withRequestBody(equalToJson("{\"enabled\":false}")));
    }

    @Test
    void testAccountByIdReturnsOk() {
        server.stubFor(post(urlEqualTo("/api/v1/accounts/test"))
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        AccountTestResult r = client.testAccount(TestAccountRequest.forAccount("fangguo", 3));
        assertTrue(r.ok());
        assertNull(r.error());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/accounts/test"))
                .withRequestBody(matchingJsonPath("$.account_id", equalTo("3")))
                .withRequestBody(matchingJsonPath("$.system", equalTo("fangguo"))));
    }

    @Test
    void testAccountSurfacesLoginFailure() {
        server.stubFor(post(urlEqualTo("/api/v1/accounts/test"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"ok\":false,\"error\":\"账号不存在\"}")));

        AccountTestResult r = client.testAccount(
                TestAccountRequest.withCredentials("hihumbird", "u1", "p1"));
        assertFalse(r.ok());
        assertEquals("账号不存在", r.error());
        server.verify(postRequestedFor(urlEqualTo("/api/v1/accounts/test"))
                .withRequestBody(matchingJsonPath("$.username", equalTo("u1")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("p1"))));
    }

    // ───── misc ───────────────────────────────────────────────────────

    @Test
    void meReturnsAdminScope() {
        server.stubFor(get(urlEqualTo("/api/v1/me"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"name\":\"admin-key\",\"scopes\":[\"admin\"]}")));

        MeResponse resp = client.me();
        assertEquals("admin-key", resp.name());
        assertEquals(1, resp.scopes().size());
        assertEquals("admin", resp.scopes().get(0));
    }

    @Test
    void apiKeyHeaderSentOnEveryCall() {
        server.stubFor(get(urlEqualTo("/api/v1/me"))
                .willReturn(aResponse().withStatus(200).withBody(
                        "{\"name\":\"k\",\"scopes\":[\"admin\"]}")));
        client.me();
        server.verify(getRequestedFor(urlEqualTo("/api/v1/me"))
                .withHeader("X-API-Key", equalTo("test-key")));
    }
}
