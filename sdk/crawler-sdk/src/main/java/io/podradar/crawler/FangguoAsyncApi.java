package io.podradar.crawler;

import io.podradar.crawler.model.FangguoItemRetryResponse;
import io.podradar.crawler.model.FangguoItemsFilter;
import io.podradar.crawler.model.FangguoItemsResponse;
import io.podradar.crawler.model.FangguoRetryResponse;
import io.podradar.crawler.model.FangguoRun;
import io.podradar.crawler.model.FangguoRunRequest;
import io.podradar.crawler.model.FangguoRunResponse;
import io.podradar.crawler.model.FangguoRunsListResponse;
import io.podradar.crawler.model.FangguoSettings;
import io.podradar.crawler.model.FangguoSettingsResponse;
import io.podradar.crawler.model.FangguoStopResponse;
import io.podradar.crawler.model.FangguoSyncState;
import io.podradar.sdk.internal.HttpExecutor;
import io.podradar.sdk.internal.Json;
import io.podradar.sdk.internal.JsonReader;
import io.podradar.sdk.internal.JsonWriter;
import io.podradar.sdk.model.PageQuery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async sibling of {@link FangguoApi}: same {@code /api/v1/fangguo/*} endpoints, each returning a
 * {@link CompletableFuture}. Obtained via {@link CrawlerAsyncClient#fangguo()} and sharing the
 * parent client's {@link HttpExecutor}.
 */
public final class FangguoAsyncApi {
    private static final String BASE = "/api/v1/fangguo";

    private final HttpExecutor http;

    FangguoAsyncApi(HttpExecutor http) {
        this.http = http;
    }

    // ───── settings + cursor ──────────────────────────────────────────

    public CompletableFuture<FangguoSettingsResponse> getSettings() {
        return http.getJsonAsync(BASE + "/settings")
                .thenApply(b -> FangguoSettingsResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoSettings> updateSettings(FangguoSettings s) {
        return http.putJsonAsync(BASE + "/settings", JsonWriter.write(s.toJson()))
                .thenApply(b -> FangguoSettings.fromJson(Json.obj(JsonReader.parseObject(b), "settings")));
    }

    public CompletableFuture<FangguoSyncState> setCursor(String at) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("at", at);
        return http.postJsonAsync(BASE + "/cursor", JsonWriter.write(json))
                .thenApply(b -> FangguoSyncState.fromJson(Json.obj(JsonReader.parseObject(b), "state")));
    }

    // ───── runs ───────────────────────────────────────────────────────

    public CompletableFuture<FangguoRunResponse> startRun(FangguoRunRequest req) {
        return http.postJsonAsync(BASE + "/runs", JsonWriter.write(req.toJson()))
                .thenApply(b -> FangguoRunResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoRunsListResponse> listRuns(PageQuery page) {
        String path = BASE + "/runs?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path)
                .thenApply(b -> FangguoRunsListResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoRun> getRun(long runId) {
        return http.getJsonAsync(BASE + "/runs/" + runId)
                .thenApply(b -> FangguoRun.fromJson(Json.obj(JsonReader.parseObject(b), "run")));
    }

    public CompletableFuture<FangguoItemsResponse> listRunItems(long runId, PageQuery page) {
        String path = BASE + "/runs/" + runId + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path)
                .thenApply(b -> FangguoItemsResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoStopResponse> stopRun(long runId) {
        return stopRun(runId, false);
    }

    public CompletableFuture<FangguoStopResponse> stopRun(long runId, boolean pauseAuto) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("pause_auto", pauseAuto);
        return http.postJsonAsync(BASE + "/runs/" + runId + "/stop", JsonWriter.write(json))
                .thenApply(b -> FangguoStopResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoRetryResponse> retryFailedRun(long runId) {
        return http.postJsonAsync(BASE + "/runs/" + runId + "/retry-failed", "{}")
                .thenApply(b -> FangguoRetryResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoRetryResponse> retryFailedAll() {
        return http.postJsonAsync(BASE + "/retry-failed", "{}")
                .thenApply(b -> FangguoRetryResponse.fromJson(JsonReader.parseObject(b)));
    }

    // ───── items ──────────────────────────────────────────────────────

    public CompletableFuture<FangguoItemsResponse> listItems(FangguoItemsFilter filter) {
        String path = BASE + "/items" + CrawlerClient.qs(filter.toQueryParams());
        return http.getJsonAsync(path)
                .thenApply(b -> FangguoItemsResponse.fromJson(JsonReader.parseObject(b)));
    }

    public CompletableFuture<FangguoItemRetryResponse> retryItem(long itemId) {
        return http.postJsonAsync(BASE + "/items/" + itemId + "/retry", "{}")
                .thenApply(b -> FangguoItemRetryResponse.fromJson(JsonReader.parseObject(b)));
    }

    /** {@code force=true} → re-enqueue ALL assets+labels of this order unit, not just failed (see {@link FangguoApi#retryItem(long, boolean)}). */
    public CompletableFuture<FangguoItemRetryResponse> retryItem(long itemId, boolean force) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("force", force);
        return http.postJsonAsync(BASE + "/items/" + itemId + "/retry", JsonWriter.write(json))
                .thenApply(b -> FangguoItemRetryResponse.fromJson(JsonReader.parseObject(b)));
    }
}
