package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * Response of {@code POST /api/v1/fangguo/runs/{id}/stop}: {@code status} is {@code "stopped"} on
 * success, with the number of still-pending assets/labels that were flushed. {@code pauseAuto}
 * echoes whether automatic sync was also paused. A run that is not currently running yields a 409
 * ({@code PodRadarConflictException}); a missing run yields a 404.
 */
public final class FangguoStopResponse {
    private final String status;
    private final long runId;
    private final boolean pauseAuto;
    private final int stoppedAssets;
    private final int stoppedLabels;

    public FangguoStopResponse(String status, long runId, boolean pauseAuto, int stoppedAssets,
                               int stoppedLabels) {
        this.status = status;
        this.runId = runId;
        this.pauseAuto = pauseAuto;
        this.stoppedAssets = stoppedAssets;
        this.stoppedLabels = stoppedLabels;
    }

    public String status()     { return status; }
    public long runId()        { return runId; }
    public boolean pauseAuto() { return pauseAuto; }
    public int stoppedAssets() { return stoppedAssets; }
    public int stoppedLabels() { return stoppedLabels; }

    public static FangguoStopResponse fromJson(Map<String, Object> o) {
        return new FangguoStopResponse(
                Json.str(o, "status"),
                Json.lng(o, "run_id"),
                Json.bool(o, "pause_auto"),
                Json.integ(o, "stopped_assets"),
                Json.integ(o, "stopped_labels"));
    }
}
