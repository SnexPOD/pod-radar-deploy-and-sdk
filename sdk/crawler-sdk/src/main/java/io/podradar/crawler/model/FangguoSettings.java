package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 5-field fangguo sync settings block (shared by {@code GET}/{@code PUT /settings}).
 * Smaller than hihumbird's — fangguo has no rescan-pending knobs (its assets are public direct
 * links, so there is no headless harvest to re-scan). On {@code PUT} the server validates each
 * field and rejects out-of-range values with a 400 ({@code PodRadarValidationException}) rather
 * than clamping them: {@code syncIntervalMinutes} 5–1440, {@code syncOverlapMinutes} 0–10080,
 * {@code maxRunSpanHours} 1–744 (the 744h / 31-day cover/task window ceiling).
 */
public final class FangguoSettings {
    private final boolean syncEnabled;
    private final int syncIntervalMinutes;
    private final int syncOverlapMinutes;
    private final String cursorStartAt;
    private final int maxRunSpanHours;

    public FangguoSettings(boolean syncEnabled, int syncIntervalMinutes, int syncOverlapMinutes,
                           String cursorStartAt, int maxRunSpanHours) {
        this.syncEnabled = syncEnabled;
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.syncOverlapMinutes = syncOverlapMinutes;
        this.cursorStartAt = cursorStartAt;
        this.maxRunSpanHours = maxRunSpanHours;
    }

    public boolean syncEnabled()      { return syncEnabled; }
    public int syncIntervalMinutes()  { return syncIntervalMinutes; }
    public int syncOverlapMinutes()   { return syncOverlapMinutes; }
    public String cursorStartAt()     { return cursorStartAt; }
    public int maxRunSpanHours()      { return maxRunSpanHours; }

    public static FangguoSettings fromJson(Map<String, Object> o) {
        return new FangguoSettings(
                Json.bool(o, "sync_enabled"),
                Json.integ(o, "sync_interval_minutes"),
                Json.integ(o, "sync_overlap_minutes"),
                Json.str(o, "cursor_start_at"),
                Json.integ(o, "max_run_span_hours"));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("sync_enabled", syncEnabled);
        o.put("sync_interval_minutes", syncIntervalMinutes);
        o.put("sync_overlap_minutes", syncOverlapMinutes);
        o.put("cursor_start_at", cursorStartAt);
        o.put("max_run_span_hours", maxRunSpanHours);
        return o;
    }
}
