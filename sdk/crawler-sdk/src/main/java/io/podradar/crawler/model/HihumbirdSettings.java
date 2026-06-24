package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** The 11-field hihumbird sync settings block (shared by {@code GET}/{@code PUT /settings}). */
public final class HihumbirdSettings {
    private static final boolean DEFAULT_RESCAN_MISSING_BATCH_ENABLED = true;
    private static final int DEFAULT_RESCAN_MISSING_BATCH_INTERVAL_MINUTES = 90;
    private static final int DEFAULT_RESCAN_MISSING_BATCH_MAX_AGE_DAYS = 30;

    private final boolean syncEnabled;
    private final int syncIntervalMinutes;
    private final int syncOverlapMinutes;
    private final String cursorStartAt;
    private final int maxRunSpanHours;
    private final boolean rescanPendingEnabled;
    private final int rescanPendingIntervalMinutes;
    private final int rescanPendingMaxAgeDays;
    private final boolean rescanMissingBatchEnabled;
    private final int rescanMissingBatchIntervalMinutes;
    private final int rescanMissingBatchMaxAgeDays;

    public HihumbirdSettings(boolean syncEnabled, int syncIntervalMinutes, int syncOverlapMinutes,
                             String cursorStartAt, int maxRunSpanHours, boolean rescanPendingEnabled,
                             int rescanPendingIntervalMinutes, int rescanPendingMaxAgeDays) {
        this(syncEnabled, syncIntervalMinutes, syncOverlapMinutes, cursorStartAt, maxRunSpanHours,
                rescanPendingEnabled, rescanPendingIntervalMinutes, rescanPendingMaxAgeDays,
                DEFAULT_RESCAN_MISSING_BATCH_ENABLED,
                DEFAULT_RESCAN_MISSING_BATCH_INTERVAL_MINUTES,
                DEFAULT_RESCAN_MISSING_BATCH_MAX_AGE_DAYS);
    }

    public HihumbirdSettings(boolean syncEnabled, int syncIntervalMinutes, int syncOverlapMinutes,
                             String cursorStartAt, int maxRunSpanHours, boolean rescanPendingEnabled,
                             int rescanPendingIntervalMinutes, int rescanPendingMaxAgeDays,
                             boolean rescanMissingBatchEnabled,
                             int rescanMissingBatchIntervalMinutes,
                             int rescanMissingBatchMaxAgeDays) {
        this.syncEnabled = syncEnabled;
        this.syncIntervalMinutes = syncIntervalMinutes;
        this.syncOverlapMinutes = syncOverlapMinutes;
        this.cursorStartAt = cursorStartAt;
        this.maxRunSpanHours = maxRunSpanHours;
        this.rescanPendingEnabled = rescanPendingEnabled;
        this.rescanPendingIntervalMinutes = rescanPendingIntervalMinutes;
        this.rescanPendingMaxAgeDays = rescanPendingMaxAgeDays;
        this.rescanMissingBatchEnabled = rescanMissingBatchEnabled;
        this.rescanMissingBatchIntervalMinutes = rescanMissingBatchIntervalMinutes;
        this.rescanMissingBatchMaxAgeDays = rescanMissingBatchMaxAgeDays;
    }

    public boolean syncEnabled() { return syncEnabled; }
    public int syncIntervalMinutes() { return syncIntervalMinutes; }
    public int syncOverlapMinutes() { return syncOverlapMinutes; }
    public String cursorStartAt() { return cursorStartAt; }
    public int maxRunSpanHours() { return maxRunSpanHours; }
    public boolean rescanPendingEnabled() { return rescanPendingEnabled; }
    public int rescanPendingIntervalMinutes() { return rescanPendingIntervalMinutes; }
    public int rescanPendingMaxAgeDays() { return rescanPendingMaxAgeDays; }
    public boolean rescanMissingBatchEnabled() { return rescanMissingBatchEnabled; }
    public int rescanMissingBatchIntervalMinutes() { return rescanMissingBatchIntervalMinutes; }
    public int rescanMissingBatchMaxAgeDays() { return rescanMissingBatchMaxAgeDays; }

    public static HihumbirdSettings fromJson(Map<String, Object> o) {
        return new HihumbirdSettings(
                Json.bool(o, "sync_enabled"),
                Json.integ(o, "sync_interval_minutes"),
                Json.integ(o, "sync_overlap_minutes"),
                Json.str(o, "cursor_start_at"),
                Json.integ(o, "max_run_span_hours"),
                Json.bool(o, "rescan_pending_enabled"),
                Json.integ(o, "rescan_pending_interval_minutes"),
                Json.integ(o, "rescan_pending_max_age_days"),
                boolOr(o, "rescan_missing_batch_enabled", DEFAULT_RESCAN_MISSING_BATCH_ENABLED),
                intOr(o, "rescan_missing_batch_interval_minutes",
                        DEFAULT_RESCAN_MISSING_BATCH_INTERVAL_MINUTES),
                intOr(o, "rescan_missing_batch_max_age_days",
                        DEFAULT_RESCAN_MISSING_BATCH_MAX_AGE_DAYS));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("sync_enabled", syncEnabled);
        o.put("sync_interval_minutes", syncIntervalMinutes);
        o.put("sync_overlap_minutes", syncOverlapMinutes);
        o.put("cursor_start_at", cursorStartAt);
        o.put("max_run_span_hours", maxRunSpanHours);
        o.put("rescan_pending_enabled", rescanPendingEnabled);
        o.put("rescan_pending_interval_minutes", rescanPendingIntervalMinutes);
        o.put("rescan_pending_max_age_days", rescanPendingMaxAgeDays);
        o.put("rescan_missing_batch_enabled", rescanMissingBatchEnabled);
        o.put("rescan_missing_batch_interval_minutes", rescanMissingBatchIntervalMinutes);
        o.put("rescan_missing_batch_max_age_days", rescanMissingBatchMaxAgeDays);
        return o;
    }

    private static boolean boolOr(Map<String, Object> o, String key, boolean fallback) {
        return o.containsKey(key) ? Json.bool(o, key) : fallback;
    }

    private static int intOr(Map<String, Object> o, String key, int fallback) {
        return o.containsKey(key) ? Json.integ(o, key) : fallback;
    }
}
