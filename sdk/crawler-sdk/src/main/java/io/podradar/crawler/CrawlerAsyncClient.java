package io.podradar.crawler;

import io.podradar.crawler.model.CrawlerAccount;
import io.podradar.crawler.model.CrawlerKey;
import io.podradar.crawler.model.AccountTestResult;
import io.podradar.crawler.model.CreateAccountRequest;
import io.podradar.crawler.model.TestAccountRequest;
import io.podradar.crawler.model.UpdateAccountRequest;
import io.podradar.crawler.model.CreateKeyResponse;
import io.podradar.crawler.model.HihumbirdSettings;
import io.podradar.crawler.model.ItemRetryResponse;
import io.podradar.crawler.model.ItemsFilter;
import io.podradar.crawler.model.ItemsListResponse;
import io.podradar.crawler.model.HihumbirdSyncState;
import io.podradar.crawler.model.MeResponse;
import io.podradar.crawler.model.RescanResponse;
import io.podradar.crawler.model.RetryFailedKindRequest;
import io.podradar.crawler.model.RetryFailedKindResponse;
import io.podradar.crawler.model.RetryRunResponse;
import io.podradar.crawler.model.RunRequest;
import io.podradar.crawler.model.StopRetryResponse;
import io.podradar.crawler.model.RunResponse;
import io.podradar.crawler.model.RunsListResponse;
import io.podradar.crawler.model.SettingsResponse;
import io.podradar.sdk.internal.HttpExecutor;
import io.podradar.sdk.internal.Json;
import io.podradar.sdk.internal.JsonReader;
import io.podradar.sdk.internal.JsonWriter;
import io.podradar.sdk.internal.SdkConfig;
import io.podradar.sdk.model.PageQuery;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Async sibling of {@link CrawlerClient}. */
public final class CrawlerAsyncClient implements AutoCloseable {
    private static final String API = "/api/v1";

    private final HttpExecutor http;
    private FangguoAsyncApi fangguo;

    private CrawlerAsyncClient(HttpExecutor http) {
        this.http = http;
    }

    public static Builder builder() { return new Builder(); }

    public static CrawlerAsyncClient wrap(CrawlerClient sync) {
        return new CrawlerAsyncClient(sync.http());
    }

    /** Async accessor for the fangguo endpoints under {@code /api/v1/fangguo/*}; lazily cached. */
    public FangguoAsyncApi fangguo() {
        if (fangguo == null) fangguo = new FangguoAsyncApi(http);
        return fangguo;
    }

    // ───── settings ───────────────────────────────────────────────────

    public CompletableFuture<SettingsResponse> getSettings() {
        return http.getJsonAsync(API + "/hihumbird/settings")
                .thenApply(b -> SettingsResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<HihumbirdSettings> updateSettings(HihumbirdSettings s) {
        return http.putJsonAsync(API + "/hihumbird/settings", JsonWriter.write(s.toJson()))
                .thenApply(b -> HihumbirdSettings.fromJson(Json.obj(JsonReader.parseObject(b), "settings")));
    }

    /** Per-account effective settings (global default merged with this account's override). */
    public CompletableFuture<SettingsResponse> getSettings(long accountId) {
        return http.getJsonAsync(API + "/hihumbird/settings?account_id=" + accountId)
                .thenApply(b -> SettingsResponse.fromJson(JsonReader.parseObject(b)));
    }

    /** Write this account's settings override (full set). */
    public CompletableFuture<HihumbirdSettings> updateSettings(long accountId, HihumbirdSettings s) {
        return http.putJsonAsync(API + "/hihumbird/settings?account_id=" + accountId, JsonWriter.write(s.toJson()))
                .thenApply(b -> HihumbirdSettings.fromJson(Json.obj(JsonReader.parseObject(b), "settings")));
    }

    // ───── runs ───────────────────────────────────────────────────────

    public CompletableFuture<RunResponse> startRun(RunRequest req) {
        return http.postJsonAsync(API + "/hihumbird/runs", JsonWriter.write(req.toJson()))
                .thenApply(b -> RunResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<RunsListResponse> listRuns(PageQuery page) {
        String path = API + "/hihumbird/runs?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path).thenApply(b -> RunsListResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<RetryRunResponse> retryFailedRun(long runId) {
        return http.postJsonAsync(API + "/hihumbird/runs/" + runId + "/retry-failed", "{}")
                .thenApply(b -> RetryRunResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<RescanResponse> rescanPendingLabels() {
        return http.postJsonAsync(API + "/hihumbird/rescan-pending-labels", "{}")
                .thenApply(b -> RescanResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<RescanResponse> rescanMissingBatches() {
        return http.postJsonAsync(API + "/hihumbird/rescan-missing-batches", "{}")
                .thenApply(b -> RescanResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<RescanResponse> rescanMissingBatches(long accountId) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("account_id", accountId);
        return http.postJsonAsync(API + "/hihumbird/rescan-missing-batches", JsonWriter.write(json))
                .thenApply(b -> RescanResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<RetryFailedKindResponse> retryFailedKind(RetryFailedKindRequest req) {
        return http.postJsonAsync(API + "/hihumbird/retry-failed", JsonWriter.write(req.toJson()))
                .thenApply(b -> RetryFailedKindResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<StopRetryResponse> stopRetry(long runId) {
        return http.postJsonAsync(API + "/hihumbird/runs/" + runId + "/stop-retry", "{}")
                .thenApply(b -> StopRetryResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<HihumbirdSyncState> setCursor(String at) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("at", at);
        return http.postJsonAsync(API + "/hihumbird/cursor", JsonWriter.write(json))
                .thenApply(b -> HihumbirdSyncState.fromJson(Json.obj(JsonReader.parseObject(b), "state")));
    }

    public CompletableFuture<ItemsListResponse> listRunItems(long runId, PageQuery page) {
        String path = API + "/hihumbird/runs/" + runId + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path).thenApply(b -> ItemsListResponse.fromJson(JsonReader.parseObject(b)));
    }

    // ───── items ──────────────────────────────────────────────────────

    public CompletableFuture<ItemsListResponse> listItems(ItemsFilter filter) {
        String path = API + "/hihumbird/items" + CrawlerClient.qs(filter.toQueryParams());
        return http.getJsonAsync(path).thenApply(b -> ItemsListResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<ItemRetryResponse> retryItem(long itemId) {
        return http.postJsonAsync(API + "/hihumbird/items/" + itemId + "/retry", "{}")
                .thenApply(b -> new ItemRetryResponse(JsonReader.parseObject(b)));
    }

    /** {@code force=true} → re-enqueue ALL assets+labels of this item, not just failed (see {@link CrawlerClient#retryItem(long, boolean)}). */
    public CompletableFuture<ItemRetryResponse> retryItem(long itemId, boolean force) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("force", force);
        return http.postJsonAsync(API + "/hihumbird/items/" + itemId + "/retry", JsonWriter.write(json))
                .thenApply(b -> new ItemRetryResponse(JsonReader.parseObject(b)));
    }

    // ───── keys ───────────────────────────────────────────────────────

    public CompletableFuture<List<CrawlerKey>> listKeys() {
        return http.getJsonAsync(API + "/keys").thenApply(b -> {
            Map<String, Object> o = JsonReader.parseObject(b);
            List<CrawlerKey> out = new ArrayList<>();
            for (Object raw : Json.list(o, "keys")) {
                out.add(CrawlerKey.fromJson(Json.asMap(raw)));
            }
            return Collections.unmodifiableList(out);
        });
    }

    public CompletableFuture<CreateKeyResponse> createKey(String name) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", name);
        return http.postJsonAsync(API + "/keys", JsonWriter.write(json))
                .thenApply(b -> CreateKeyResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<Void> revokeKey(long id) {
        return http.postJsonAsync(API + "/keys/" + id + "/revoke", "{}").thenApply(b -> null);
    }

    public CompletableFuture<Void> deleteKey(long id) {
        return http.deleteJsonAsync(API + "/keys/" + id).thenApply(b -> null);
    }

    // ───── accounts (upstream login accounts) ──────────────────────────

    public CompletableFuture<List<CrawlerAccount>> listAccounts() {
        return listAccounts(null);
    }

    public CompletableFuture<List<CrawlerAccount>> listAccounts(String system) {
        String path = API + "/accounts" + (system != null ? "?system=" + CrawlerClient.urlEncode(system) : "");
        return http.getJsonAsync(path).thenApply(b -> {
            Map<String, Object> o = JsonReader.parseObject(b);
            List<CrawlerAccount> out = new ArrayList<>();
            for (Object raw : Json.list(o, "accounts")) {
                out.add(CrawlerAccount.fromJson(Json.asMap(raw)));
            }
            return Collections.unmodifiableList(out);
        });
    }

    public CompletableFuture<CrawlerAccount> createAccount(CreateAccountRequest req) {
        return http.postJsonAsync(API + "/accounts", JsonWriter.write(req.toJson()))
                .thenApply(b -> CrawlerAccount.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<CrawlerAccount> updateAccount(long id, UpdateAccountRequest req) {
        return http.putJsonAsync(API + "/accounts/" + id, JsonWriter.write(req.toJson()))
                .thenApply(b -> CrawlerAccount.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<CrawlerAccount> setAccountEnabled(long id, boolean enabled) {
        return updateAccount(id, UpdateAccountRequest.empty().withEnabled(enabled));
    }

    public CompletableFuture<Void> deleteAccount(long id) {
        return http.deleteJsonAsync(API + "/accounts/" + id).thenApply(b -> null);
    }

    /** Verify an upstream login without saving. Failed login → {@code ok=false} (not an exception). */
    public CompletableFuture<AccountTestResult> testAccount(TestAccountRequest req) {
        return http.postJsonAsync(API + "/accounts/test", JsonWriter.write(req.toJson()))
                .thenApply(b -> AccountTestResult.fromJson(JsonReader.parseObject(b)));
    }

    // ───── misc ───────────────────────────────────────────────────────

    public CompletableFuture<MeResponse> me() {
        return http.getJsonAsync(API + "/me")
                .thenApply(b -> MeResponse.fromJson(JsonReader.parseObject(b)));
    }

    /** Returns the read-only effective config. */
    public SdkConfig config() { return http.config(); }

    @Override
    public void close() {
        http.close(); // shuts down the async pool
    }

    // ───── Builder ────────────────────────────────────────────────────

    public static final class Builder {
        private final SdkConfig.Builder cfg = SdkConfig.builder();

        public Builder() {
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

        public CrawlerAsyncClient build() {
            return new CrawlerAsyncClient(new HttpExecutor(cfg.build()));
        }
    }
}
