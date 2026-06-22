package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fangguo sync run. Returned as a list row by {@code GET /runs}, and singly (unwrapped from
 * {@code {run}}) by {@code GET /runs/{id}}. {@code id} and {@code runId} are the same value.
 * The {@code live} block reflects the asset/label queue state at query time, separate from the
 * frozen {@code counts} snapshot.
 */
public final class FangguoRun {
    private final long id;
    private final long runId;
    private final String trigger;
    private final String mode;
    private final String windowFrom;
    private final String windowTo;
    private final String status;
    private final String error;
    private final String startedAt;
    private final String finishedAt;
    private final int queued;
    private final int fetched;
    private final int failed;
    private final int duplicate;
    private final Map<String, Object> counts;
    private final Map<String, Object> jobParams;
    private final Live live;

    public FangguoRun(long id, long runId, String trigger, String mode, String windowFrom,
                      String windowTo, String status, String error, String startedAt, String finishedAt,
                      int queued, int fetched, int failed, int duplicate, Map<String, Object> counts,
                      Map<String, Object> jobParams, Live live) {
        this.id = id;
        this.runId = runId;
        this.trigger = trigger;
        this.mode = mode;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.status = status;
        this.error = error;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.queued = queued;
        this.fetched = fetched;
        this.failed = failed;
        this.duplicate = duplicate;
        this.counts = counts;
        this.jobParams = jobParams;
        this.live = live;
    }

    public long id()                       { return id; }
    public long runId()                    { return runId; }
    public String trigger()                { return trigger; }
    public String mode()                   { return mode; }
    public String windowFrom()             { return windowFrom; }
    public String windowTo()               { return windowTo; }
    public String status()                 { return status; }
    public String error()                  { return error; }
    public String startedAt()              { return startedAt; }
    public String finishedAt()             { return finishedAt; }
    public int queued()                    { return queued; }
    public int fetched()                   { return fetched; }
    public int failed()                    { return failed; }
    public int duplicate()                 { return duplicate; }
    public Map<String, Object> counts()    { return counts; }
    public Map<String, Object> jobParams() { return jobParams; }
    public Live live()                     { return live; }

    public static FangguoRun fromJson(Map<String, Object> o) {
        return new FangguoRun(
                Json.lng(o, "id"),
                o.containsKey("run_id") ? Json.lng(o, "run_id") : Json.lng(o, "id"),
                Json.str(o, "trigger"),
                Json.str(o, "mode"),
                Json.str(o, "window_from"),
                Json.str(o, "window_to"),
                Json.str(o, "status"),
                Json.str(o, "error"),
                Json.str(o, "started_at"),
                Json.str(o, "finished_at"),
                Json.integ(o, "queued"),
                Json.integ(o, "fetched"),
                Json.integ(o, "failed"),
                Json.integ(o, "duplicate"),
                Json.obj(o, "counts"),
                Json.obj(o, "job_params"),
                Live.fromJson(Json.obj(o, "live")));
    }

    /** The {@code live} block: current per-kind asset tallies plus per-status label counts. */
    public static final class Live {
        private final Map<String, FangguoAssetStatusCounts> assets;
        private final Map<String, Integer> labels;

        public Live(Map<String, FangguoAssetStatusCounts> assets, Map<String, Integer> labels) {
            this.assets = assets;
            this.labels = labels;
        }

        /** Per-kind asset tallies, keyed by {@code effect_image} / {@code production_image} / {@code finished_image}. */
        public Map<String, FangguoAssetStatusCounts> assets() { return assets; }

        /** Label counts keyed by status (e.g. {@code pending}, {@code converted}, {@code failed}). */
        public Map<String, Integer> labels() { return labels; }

        public FangguoAssetStatusCounts effectImage()     { return assetOrEmpty("effect_image"); }
        public FangguoAssetStatusCounts productionImage() { return assetOrEmpty("production_image"); }
        public FangguoAssetStatusCounts finishedImage()   { return assetOrEmpty("finished_image"); }

        private FangguoAssetStatusCounts assetOrEmpty(String kind) {
            FangguoAssetStatusCounts c = assets.get(kind);
            return c != null ? c : new FangguoAssetStatusCounts(0, 0, 0, 0);
        }

        static Live fromJson(Map<String, Object> o) {
            Map<String, FangguoAssetStatusCounts> assets = new LinkedHashMap<>();
            Map<String, Object> rawAssets = Json.obj(o, "assets");
            for (Map.Entry<String, Object> e : rawAssets.entrySet()) {
                assets.put(e.getKey(), FangguoAssetStatusCounts.fromJson(Json.asMap(e.getValue())));
            }
            Map<String, Integer> labels = new LinkedHashMap<>();
            Map<String, Object> rawLabels = Json.obj(o, "labels");
            for (Map.Entry<String, Object> e : rawLabels.entrySet()) {
                Object v = e.getValue();
                labels.put(e.getKey(), v instanceof Number ? ((Number) v).intValue() : 0);
            }
            return new Live(
                    Collections.unmodifiableMap(assets),
                    Collections.unmodifiableMap(labels));
        }
    }
}
