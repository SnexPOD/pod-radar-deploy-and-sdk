package io.podradar.crawler;

import io.podradar.crawler.model.AccountTestResult;
import io.podradar.crawler.model.CrawlerAccount;
import io.podradar.crawler.model.CrawlerKey;
import io.podradar.crawler.model.CreateAccountRequest;
import io.podradar.crawler.model.TestAccountRequest;
import io.podradar.crawler.model.CreateKeyResponse;
import io.podradar.crawler.model.UpdateAccountRequest;
import io.podradar.crawler.model.HihumbirdSettings;
import io.podradar.crawler.model.HihumbirdSyncState;
import io.podradar.crawler.model.ItemRetryResponse;
import io.podradar.crawler.model.ItemsFilter;
import io.podradar.crawler.model.ItemsListResponse;
import io.podradar.crawler.model.MeResponse;
import io.podradar.crawler.model.RescanResponse;
import io.podradar.crawler.model.RetryFailedKindRequest;
import io.podradar.crawler.model.RetryFailedKindResponse;
import io.podradar.crawler.model.RetryRunResponse;
import io.podradar.crawler.model.RunRequest;
import io.podradar.crawler.model.RunResponse;
import io.podradar.crawler.model.RunsListResponse;
import io.podradar.crawler.model.SettingsResponse;
import io.podradar.crawler.model.StopRetryResponse;
import io.podradar.sdk.internal.HttpExecutor;
import io.podradar.sdk.internal.Json;
import io.podradar.sdk.internal.JsonReader;
import io.podradar.sdk.internal.JsonWriter;
import io.podradar.sdk.internal.SdkConfig;
import io.podradar.sdk.internal.Urls;
import io.podradar.sdk.model.PageQuery;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous client for the pod-radar crawler service ({@code /api/v1/hihumbird/*} +
 * {@code /api/v1/keys/*}). Internal admin tool — not published to Maven Central.
 */
public final class CrawlerClient implements AutoCloseable {
    private static final String API = "/api/v1";

    private final HttpExecutor http;
    private FangguoApi fangguo;

    private CrawlerClient(HttpExecutor http) {
        this.http = http;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Accessor for the fangguo (方果ERP) endpoints under {@code /api/v1/fangguo/*}. Lazily created
     * and cached; shares this client's connection, endpoint, and API key. The hihumbird methods on
     * this client are unrelated to fangguo and remain unchanged.
     */
    public FangguoApi fangguo() {
        if (fangguo == null) fangguo = new FangguoApi(http);
        return fangguo;
    }

    // ───── settings ───────────────────────────────────────────────────

    public SettingsResponse getSettings() {
        String body = http.getJson(API + "/hihumbird/settings");
        return SettingsResponse.fromJson(JsonReader.parseObject(body));
    }

    public HihumbirdSettings updateSettings(HihumbirdSettings s) {
        String body = http.putJson(API + "/hihumbird/settings", JsonWriter.write(s.toJson()));
        Map<String, Object> o = JsonReader.parseObject(body);
        return HihumbirdSettings.fromJson(Json.obj(o, "settings"));
    }

    /** Per-account effective settings (global default merged with this account's override). */
    public SettingsResponse getSettings(long accountId) {
        String body = http.getJson(API + "/hihumbird/settings?account_id=" + accountId);
        return SettingsResponse.fromJson(JsonReader.parseObject(body));
    }

    /** Write this account's settings override (full set; each account schedules on its own cursor/interval). */
    public HihumbirdSettings updateSettings(long accountId, HihumbirdSettings s) {
        String body = http.putJson(API + "/hihumbird/settings?account_id=" + accountId, JsonWriter.write(s.toJson()));
        Map<String, Object> o = JsonReader.parseObject(body);
        return HihumbirdSettings.fromJson(Json.obj(o, "settings"));
    }

    // ───── runs ───────────────────────────────────────────────────────

    public RunResponse startRun(RunRequest req) {
        String body = http.postJson(API + "/hihumbird/runs", JsonWriter.write(req.toJson()));
        return RunResponse.fromJson(JsonReader.parseObject(body));
    }

    public RunsListResponse listRuns(PageQuery page) {
        String path = API + "/hihumbird/runs?limit=" + page.limit() + "&offset=" + page.offset();
        return RunsListResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    public RetryRunResponse retryFailedRun(long runId) {
        String body = http.postJson(API + "/hihumbird/runs/" + runId + "/retry-failed", "{}");
        return RetryRunResponse.fromJson(JsonReader.parseObject(body));
    }

    public RescanResponse rescanPendingLabels() {
        String body = http.postJson(API + "/hihumbird/rescan-pending-labels", "{}");
        return RescanResponse.fromJson(JsonReader.parseObject(body));
    }

    /**
     * Rescan local hihumbird rows that still have no production batch code. The server searches the
     * upstream order API by order id (chunked by 100) and only backfills order/batch metadata; it
     * does not enqueue images or labels. If another hihumbird run is active, the request is queued
     * and the response status is {@code queued}.
     */
    public RescanResponse rescanMissingBatches() {
        String body = http.postJson(API + "/hihumbird/rescan-missing-batches", "{}");
        return RescanResponse.fromJson(JsonReader.parseObject(body));
    }

    /** Same as {@link #rescanMissingBatches()}, scoped to one upstream account. */
    public RescanResponse rescanMissingBatches(long accountId) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("account_id", accountId);
        String body = http.postJson(API + "/hihumbird/rescan-missing-batches", JsonWriter.write(json));
        return RescanResponse.fromJson(JsonReader.parseObject(body));
    }

    /**
     * Retry all failed assets of one kind within the request's filter (the "重试所有失败X"
     * buttons). Creates {@code status='done'} enqueue-type batch(es); product_image is
     * auto-chunked by 1000 and re-rendered via the headless browser. Returns {@code status="empty"}
     * when nothing failed under the filter.
     */
    public RetryFailedKindResponse retryFailedKind(RetryFailedKindRequest req) {
        String body = http.postJson(API + "/hihumbird/retry-failed", JsonWriter.write(req.toJson()));
        return RetryFailedKindResponse.fromJson(JsonReader.parseObject(body));
    }

    /** Stop an in-progress enqueue-type retry batch (signals harvest to stop + flushes its queue). */
    public StopRetryResponse stopRetry(long runId) {
        String body = http.postJson(API + "/hihumbird/runs/" + runId + "/stop-retry", "{}");
        return StopRetryResponse.fromJson(JsonReader.parseObject(body));
    }

    /**
     * Force the incremental cursor to {@code at} (ISO-8601 / datetime-local), or clear it with
     * {@code null} (back to "no cursor"). Takes effect immediately; the next incremental sync
     * resumes from {@code at - overlap}. Returns the updated cursor state.
     */
    public HihumbirdSyncState setCursor(String at) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("at", at);
        String body = http.postJson(API + "/hihumbird/cursor", JsonWriter.write(json));
        return HihumbirdSyncState.fromJson(Json.obj(JsonReader.parseObject(body), "state"));
    }

    public ItemsListResponse listRunItems(long runId, PageQuery page) {
        String path = API + "/hihumbird/runs/" + runId + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return ItemsListResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    // ───── items ──────────────────────────────────────────────────────

    public ItemsListResponse listItems(ItemsFilter filter) {
        String path = API + "/hihumbird/items" + qs(filter.toQueryParams());
        return ItemsListResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    /** {@code POST /items/{id}/retry} → re-enqueue this item's FAILED assets/labels. Throws 404. */
    public ItemRetryResponse retryItem(long itemId) {
        String body = http.postJson(API + "/hihumbird/items/" + itemId + "/retry", "{}");
        return new ItemRetryResponse(JsonReader.parseObject(body));
    }

    /**
     * {@code POST /items/{id}/retry} with {@code {"force":true}}. When {@code force} is true the server
     * re-enqueues ALL of this item's assets + labels regardless of current status (including ones that
     * already {@code fetched}/{@code downloaded}/{@code converted}), not just failed ones; labels also
     * drop their cached PDF so it is re-downloaded. {@code force=false} matches {@link #retryItem(long)}.
     * Throws 404.
     */
    public ItemRetryResponse retryItem(long itemId, boolean force) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("force", force);
        String body = http.postJson(API + "/hihumbird/items/" + itemId + "/retry", JsonWriter.write(json));
        return new ItemRetryResponse(JsonReader.parseObject(body));
    }

    // ───── keys ───────────────────────────────────────────────────────

    public List<CrawlerKey> listKeys() {
        Map<String, Object> o = JsonReader.parseObject(http.getJson(API + "/keys"));
        List<CrawlerKey> out = new ArrayList<>();
        for (Object raw : Json.list(o, "keys")) {
            out.add(CrawlerKey.fromJson(Json.asMap(raw)));
        }
        return Collections.unmodifiableList(out);
    }

    public CreateKeyResponse createKey(String name) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        String body = http.postJson(API + "/keys", JsonWriter.write(json));
        return CreateKeyResponse.fromJson(JsonReader.parseObject(body));
    }

    public void revokeKey(long id) {
        http.postJson(API + "/keys/" + id + "/revoke", "{}");
    }

    public void deleteKey(long id) {
        http.deleteJson(API + "/keys/" + id);
    }

    // ───── accounts (upstream login accounts) ──────────────────────────

    /** All upstream login accounts (passwords never returned). */
    public List<CrawlerAccount> listAccounts() {
        return listAccounts(null);
    }

    /** Upstream accounts, optionally filtered by system ("hihumbird" | "fangguo"). */
    public List<CrawlerAccount> listAccounts(String system) {
        String path = API + "/accounts" + (system != null ? "?system=" + urlEncode(system) : "");
        Map<String, Object> o = JsonReader.parseObject(http.getJson(path));
        List<CrawlerAccount> out = new ArrayList<>();
        for (Object raw : Json.list(o, "accounts")) {
            out.add(CrawlerAccount.fromJson(Json.asMap(raw)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Register an upstream login account. Throws 409 if (system,name) already exists. */
    public CrawlerAccount createAccount(CreateAccountRequest req) {
        String body = http.postJson(API + "/accounts", JsonWriter.write(req.toJson()));
        return CrawlerAccount.fromJson(JsonReader.parseObject(body));
    }

    /** Partial update (uses PUT — HttpURLConnection lacks PATCH; server accepts both). Throws 404. */
    public CrawlerAccount updateAccount(long id, UpdateAccountRequest req) {
        String body = http.putJson(API + "/accounts/" + id, JsonWriter.write(req.toJson()));
        return CrawlerAccount.fromJson(JsonReader.parseObject(body));
    }

    /** Enable/disable an account — convenience over {@link #updateAccount}. */
    public CrawlerAccount setAccountEnabled(long id, boolean enabled) {
        return updateAccount(id, UpdateAccountRequest.empty().withEnabled(enabled));
    }

    /** Soft-delete (revoke) an account; already-crawled orders keep their attribution. Throws 404. */
    public void deleteAccount(long id) {
        http.deleteJson(API + "/accounts/" + id);
    }

    /**
     * Verify an upstream login without saving (makes a real login call to the upstream system).
     * A failed login returns {@code ok=false} with the reason — not an exception. Throws on
     * HTTP 429 (too many concurrent tests) or 400 (neither account_id nor username+password).
     */
    public AccountTestResult testAccount(TestAccountRequest req) {
        String body = http.postJson(API + "/accounts/test", JsonWriter.write(req.toJson()));
        return AccountTestResult.fromJson(JsonReader.parseObject(body));
    }

    // ───── misc ───────────────────────────────────────────────────────

    public MeResponse me() {
        return MeResponse.fromJson(JsonReader.parseObject(http.getJson(API + "/me")));
    }

    // ───── helpers ────────────────────────────────────────────────────

    static String qs(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(urlEncode(e.getKey()));
            sb.append('=');
            sb.append(urlEncode(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    static String urlEncode(String s) {
        return Urls.encode(s);
    }

    /** Returns the read-only effective config. */
    public SdkConfig config() { return http.config(); }

    /** Package-private accessor used by {@link CrawlerAsyncClient#wrap}. */
    HttpExecutor http() { return http; }

    @Override
    public void close() {
        http.close(); // shuts down the async pool if one was ever created
    }

    // ───── Builder ────────────────────────────────────────────────────

    public static final class Builder {
        private final SdkConfig.Builder cfg = SdkConfig.builder();

        public Builder() {
            // Crawler default user agent + longer request timeout
            cfg.userAgent("podradar-crawler-sdk/0.1.0");
            cfg.requestTimeout(Duration.ofMinutes(2));
        }

        public Builder endpoint(String url) { cfg.endpoint(url); return this; }
        public Builder endpoint(URI uri) { cfg.endpoint(uri); return this; }
        public Builder apiKey(String key) { cfg.apiKey(key); return this; }
        public Builder connectTimeout(Duration d) { cfg.connectTimeout(d); return this; }
        public Builder requestTimeout(Duration d) { cfg.requestTimeout(d); return this; }
        public Builder userAgent(String ua) { cfg.userAgent(ua); return this; }
        public Builder retryOnServerError(boolean on) { cfg.retryOnServerError(on); return this; }
        public Builder maxRetries(int n) { cfg.maxRetries(n); return this; }

        public CrawlerClient build() {
            return new CrawlerClient(new HttpExecutor(cfg.build()));
        }
    }
}
