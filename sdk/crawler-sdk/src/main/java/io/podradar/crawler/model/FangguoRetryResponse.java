package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * Response of {@code POST /api/v1/fangguo/runs/{id}/retry-failed} and {@code POST /retry-failed}.
 * {@code status} is {@code "queued"} when failed assets/labels were re-enqueued or {@code "empty"}
 * when there was nothing to retry. {@code runId} is the scoped run id, or {@code null} for the
 * whole-library {@code POST /retry-failed}.
 */
public final class FangguoRetryResponse {
    private final String status;
    private final Long runId;
    private final int requeuedAssets;
    private final int requeuedLabels;
    private final int itemCount;

    public FangguoRetryResponse(String status, Long runId, int requeuedAssets, int requeuedLabels,
                                int itemCount) {
        this.status = status;
        this.runId = runId;
        this.requeuedAssets = requeuedAssets;
        this.requeuedLabels = requeuedLabels;
        this.itemCount = itemCount;
    }

    public String status()      { return status; }
    /** Scoped run id, or {@code null} for the whole-library {@code POST /retry-failed}. */
    public Long runId()         { return runId; }
    public int requeuedAssets() { return requeuedAssets; }
    public int requeuedLabels() { return requeuedLabels; }
    public int itemCount()      { return itemCount; }

    public static FangguoRetryResponse fromJson(Map<String, Object> o) {
        return new FangguoRetryResponse(
                Json.str(o, "status"),
                nullableLong(o.get("run_id")),
                Json.integ(o, "requeued_assets"),
                Json.integ(o, "requeued_labels"),
                Json.integ(o, "item_count"));
    }

    private static Long nullableLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : null;
    }
}
