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

/**
 * Synchronous accessor for the fangguo (方果ERP) sync endpoints under {@code /api/v1/fangguo/*}.
 * Obtained via {@link CrawlerClient#fangguo()}; shares the parent client's {@link HttpExecutor}
 * (and therefore its endpoint, API key, and timeouts). Fangguo runs harvest order-unit images and
 * shipping labels; unlike hihumbird there is no rescan-pending-labels step (assets are public
 * direct links) and no per-kind bulk retry.
 */
public final class FangguoApi {
    private static final String BASE = "/api/v1/fangguo";

    private final HttpExecutor http;

    FangguoApi(HttpExecutor http) {
        this.http = http;
    }

    // ───── settings + cursor ──────────────────────────────────────────

    /** {@code GET /settings} → the 5-field settings block plus the current cursor {@code state}. */
    public FangguoSettingsResponse getSettings() {
        return FangguoSettingsResponse.fromJson(JsonReader.parseObject(http.getJson(BASE + "/settings")));
    }

    /** {@code PUT /settings} → the persisted settings; out-of-range values are rejected with a 400. */
    public FangguoSettings updateSettings(FangguoSettings s) {
        String body = http.putJson(BASE + "/settings", JsonWriter.write(s.toJson()));
        return FangguoSettings.fromJson(Json.obj(JsonReader.parseObject(body), "settings"));
    }

    /**
     * {@code POST /cursor} → force the incremental cursor to {@code at} (ISO-8601), or clear it with
     * {@code null}. Returns the updated cursor state. Throws if a run is currently in progress (409).
     */
    public FangguoSyncState setCursor(String at) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("at", at);
        String body = http.postJson(BASE + "/cursor", JsonWriter.write(json));
        return FangguoSyncState.fromJson(Json.obj(JsonReader.parseObject(body), "state"));
    }

    // ───── runs ───────────────────────────────────────────────────────

    /** {@code POST /runs} → start a run. Throws (409) if one is already running. */
    public FangguoRunResponse startRun(FangguoRunRequest req) {
        String body = http.postJson(BASE + "/runs", JsonWriter.write(req.toJson()));
        return FangguoRunResponse.fromJson(JsonReader.parseObject(body));
    }

    /** {@code GET /runs?limit&offset} → a page of runs, newest first. */
    public FangguoRunsListResponse listRuns(PageQuery page) {
        String path = BASE + "/runs?limit=" + page.limit() + "&offset=" + page.offset();
        return FangguoRunsListResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    /** {@code GET /runs/{id}} → one run (unwrapped from {@code {run}}). Throws 404 if missing. */
    public FangguoRun getRun(long runId) {
        String body = http.getJson(BASE + "/runs/" + runId);
        return FangguoRun.fromJson(Json.obj(JsonReader.parseObject(body), "run"));
    }

    /** {@code GET /runs/{id}/items?limit&offset} → the items harvested by one run. Throws 404. */
    public FangguoItemsResponse listRunItems(long runId, PageQuery page) {
        String path = BASE + "/runs/" + runId + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return FangguoItemsResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    /** {@code POST /runs/{id}/stop} → stop a running run (without pausing automatic sync). */
    public FangguoStopResponse stopRun(long runId) {
        return stopRun(runId, false);
    }

    /**
     * {@code POST /runs/{id}/stop} → stop a running run; when {@code pauseAuto} is true the scheduler
     * is also paused. Throws 409 if the run is not currently running, 404 if it does not exist.
     */
    public FangguoStopResponse stopRun(long runId, boolean pauseAuto) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("pause_auto", pauseAuto);
        String body = http.postJson(BASE + "/runs/" + runId + "/stop", JsonWriter.write(json));
        return FangguoStopResponse.fromJson(JsonReader.parseObject(body));
    }

    /** {@code POST /runs/{id}/retry-failed} → re-enqueue this run's failed assets/labels. Throws 404. */
    public FangguoRetryResponse retryFailedRun(long runId) {
        String body = http.postJson(BASE + "/runs/" + runId + "/retry-failed", "{}");
        return FangguoRetryResponse.fromJson(JsonReader.parseObject(body));
    }

    /** {@code POST /retry-failed} → re-enqueue every failed asset/label across the whole library. */
    public FangguoRetryResponse retryFailedAll() {
        String body = http.postJson(BASE + "/retry-failed", "{}");
        return FangguoRetryResponse.fromJson(JsonReader.parseObject(body));
    }

    // ───── items ──────────────────────────────────────────────────────

    /**
     * {@code GET /items?run_id&q&ship_status&crawl_status&limit&offset} → items across runs. When a
     * {@code run_id} filter is set on a run that does not exist the server returns 404 (thrown).
     */
    public FangguoItemsResponse listItems(FangguoItemsFilter filter) {
        String path = BASE + "/items" + CrawlerClient.qs(filter.toQueryParams());
        return FangguoItemsResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    /** {@code POST /items/{id}/retry} → re-enqueue one order unit's failed assets/labels. Throws 404. */
    public FangguoItemRetryResponse retryItem(long itemId) {
        String body = http.postJson(BASE + "/items/" + itemId + "/retry", "{}");
        return FangguoItemRetryResponse.fromJson(JsonReader.parseObject(body));
    }

    /**
     * {@code POST /items/{id}/retry} with {@code {"force":true}}. When {@code force} is true the server
     * re-enqueues ALL of this order unit's assets + its order's labels regardless of current status
     * (including already {@code fetched}/{@code downloaded}/{@code converted}), not just failed ones;
     * labels also drop their cached PDF so it is re-downloaded. {@code force=false} matches
     * {@link #retryItem(long)}. Throws 404.
     */
    public FangguoItemRetryResponse retryItem(long itemId, boolean force) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("force", force);
        String body = http.postJson(BASE + "/items/" + itemId + "/retry", JsonWriter.write(json));
        return FangguoItemRetryResponse.fromJson(JsonReader.parseObject(body));
    }
}
