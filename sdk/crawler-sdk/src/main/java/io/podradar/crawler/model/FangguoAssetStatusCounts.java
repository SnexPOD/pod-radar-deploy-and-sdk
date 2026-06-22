package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * Live per-status asset tally for one asset kind, under {@link FangguoRun.Live}. Reflects the
 * queue right now (not the frozen {@code counts} snapshot): how many of this kind are still
 * {@code pending}/{@code fetching} vs. terminal {@code fetched}/{@code failed}.
 */
public final class FangguoAssetStatusCounts {
    private final int pending;
    private final int fetching;
    private final int fetched;
    private final int failed;

    public FangguoAssetStatusCounts(int pending, int fetching, int fetched, int failed) {
        this.pending = pending;
        this.fetching = fetching;
        this.fetched = fetched;
        this.failed = failed;
    }

    public int pending()  { return pending; }
    public int fetching() { return fetching; }
    public int fetched()  { return fetched; }
    public int failed()   { return failed; }

    public static FangguoAssetStatusCounts fromJson(Map<String, Object> o) {
        if (o == null) return new FangguoAssetStatusCounts(0, 0, 0, 0);
        return new FangguoAssetStatusCounts(
                Json.integ(o, "pending"),
                Json.integ(o, "fetching"),
                Json.integ(o, "fetched"),
                Json.integ(o, "failed"));
    }
}
