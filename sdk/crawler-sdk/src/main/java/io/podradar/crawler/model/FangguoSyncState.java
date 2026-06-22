package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * Cursor snapshot returned by {@code GET /settings} (alongside the settings block) and
 * {@code POST /cursor}. {@code nextSyncAt} is fangguo-specific (the scheduler's planned next tick).
 */
public final class FangguoSyncState {
    private final String lastSuccessAt;
    private final String lastStartedAt;
    private final Long lastRunId;
    private final String nextSyncAt;
    private final Long lastSuccessCreatedFrom;
    private final Long lastSuccessCreatedTo;

    public FangguoSyncState(String lastSuccessAt, String lastStartedAt, Long lastRunId,
                            String nextSyncAt, Long lastSuccessCreatedFrom, Long lastSuccessCreatedTo) {
        this.lastSuccessAt = lastSuccessAt;
        this.lastStartedAt = lastStartedAt;
        this.lastRunId = lastRunId;
        this.nextSyncAt = nextSyncAt;
        this.lastSuccessCreatedFrom = lastSuccessCreatedFrom;
        this.lastSuccessCreatedTo = lastSuccessCreatedTo;
    }

    public String lastSuccessAt()        { return lastSuccessAt; }
    public String lastStartedAt()        { return lastStartedAt; }
    public Long lastRunId()              { return lastRunId; }
    public String nextSyncAt()           { return nextSyncAt; }
    public Long lastSuccessCreatedFrom() { return lastSuccessCreatedFrom; }
    public Long lastSuccessCreatedTo()   { return lastSuccessCreatedTo; }

    public static FangguoSyncState fromJson(Map<String, Object> o) {
        if (o == null || o.isEmpty()) {
            return new FangguoSyncState(null, null, null, null, null, null);
        }
        Long from = null, to = null;
        Map<String, Object> range = Json.obj(o, "last_success_created_range");
        if (range != null && !range.isEmpty()) {
            from = nullableLong(range.get("from"));
            to = nullableLong(range.get("to"));
        }
        return new FangguoSyncState(
                Json.str(o, "last_success_at"),
                Json.str(o, "last_started_at"),
                nullableLong(o.get("last_run_id")),
                Json.str(o, "next_sync_at"),
                from,
                to);
    }

    private static Long nullableLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : null;
    }
}
