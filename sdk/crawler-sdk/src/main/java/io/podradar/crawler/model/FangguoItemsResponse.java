package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Response of {@code GET /runs/{id}/items} and {@code GET /items}. {@code run} is the owning run on
 * a run-scoped query and {@code null} on a cross-run {@code /items} query. {@code detailSource} is
 * {@code "run_items"} when scoped to a run, {@code "all"} otherwise.
 */
public final class FangguoItemsResponse {
    private final FangguoRun run;
    private final List<FangguoItem> items;
    private final int total;
    private final int limit;
    private final int offset;
    private final String detailSource;

    public FangguoItemsResponse(FangguoRun run, List<FangguoItem> items, int total, int limit,
                                int offset, String detailSource) {
        this.run = run;
        this.items = items;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
        this.detailSource = detailSource;
    }

    /** The owning run on a run-scoped query; {@code null} on a cross-run {@code /items} query. */
    public FangguoRun run()          { return run; }
    public List<FangguoItem> items() { return items; }
    public int total()               { return total; }
    public int limit()               { return limit; }
    public int offset()              { return offset; }
    /** {@code "run_items"} when scoped to a run, {@code "all"} for a cross-run query. */
    public String detailSource()     { return detailSource; }

    public static FangguoItemsResponse fromJson(Map<String, Object> o) {
        List<FangguoItem> items = new ArrayList<>();
        for (Object raw : Json.list(o, "items")) {
            items.add(FangguoItem.fromJson(Json.asMap(raw)));
        }
        Object rawRun = o.get("run");
        FangguoRun run = rawRun instanceof Map ? FangguoRun.fromJson(Json.asMap(rawRun)) : null;
        return new FangguoItemsResponse(
                run,
                Collections.unmodifiableList(items),
                Json.integ(o, "total"),
                Json.integ(o, "limit"),
                Json.integ(o, "offset"),
                Json.str(o, "detail_source"));
    }
}
