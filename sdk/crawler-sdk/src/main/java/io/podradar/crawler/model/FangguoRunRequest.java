package io.podradar.crawler.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder for {@code POST /api/v1/fangguo/runs}. Unlike hihumbird there is no
 * {@code batch_code}: a fangguo run is identified solely by its {@code run_id}.
 */
public final class FangguoRunRequest {

    public enum Mode { INCREMENTAL, BACKFILL }

    private final Mode mode;
    private final Long from;
    private final Long to;
    private boolean dryRun;

    private FangguoRunRequest(Mode mode, Long from, Long to) {
        this.mode = mode;
        this.from = from;
        this.to = to;
    }

    /** Resume from the server-side cursor. */
    public static FangguoRunRequest incremental() {
        return new FangguoRunRequest(Mode.INCREMENTAL, null, null);
    }

    /**
     * Backfill a closed time window (epoch milliseconds, {@code from < to}). The server clamps the
     * window to at most 31 days — the cover/task upstream rejects anything wider.
     */
    public static FangguoRunRequest backfill(long fromMs, long toMs) {
        if (fromMs >= toMs) throw new IllegalArgumentException("from must be < to");
        return new FangguoRunRequest(Mode.BACKFILL, fromMs, toMs);
    }

    public FangguoRunRequest withDryRun(boolean v) { this.dryRun = v; return this; }

    public Mode mode()      { return mode; }
    public Long from()      { return from; }
    public Long to()        { return to; }
    public boolean dryRun() { return dryRun; }

    /** JSON body shape as sent to the server. */
    public Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("mode", mode == Mode.BACKFILL ? "backfill" : "incremental");
        if (from != null) o.put("from", from);
        if (to != null) o.put("to", to);
        if (dryRun) o.put("dry_run", true);
        return o;
    }
}
