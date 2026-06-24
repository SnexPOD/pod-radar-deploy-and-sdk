package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Response of hihumbird rescan endpoints such as {@code rescan-pending-labels} and
 * {@code rescan-missing-batches}. A {@code queued} status means another hihumbird sync is active;
 * the server accepted the request and will run it when the sync lock becomes free.
 */
public final class RescanResponse {
    private final String status;
    private final long runId;
    private final OptionalInt itemCount;

    public RescanResponse(String status, long runId, OptionalInt itemCount) {
        this.status = status;
        this.runId = runId;
        this.itemCount = itemCount;
    }

    public String status()           { return status; }
    public long runId()              { return runId; }
    public OptionalInt itemCount()   { return itemCount; }
    public boolean isRunning()       { return "running".equals(status); }
    public boolean isQueued()        { return "queued".equals(status); }

    public static RescanResponse fromJson(Map<String, Object> o) {
        return new RescanResponse(
                Json.str(o, "status"),
                Json.lng(o, "run_id"),
                Json.optInt(o, "item_count"));
    }
}
