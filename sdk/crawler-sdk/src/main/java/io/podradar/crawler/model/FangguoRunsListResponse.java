package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Response of {@code GET /api/v1/fangguo/runs}: a page of runs plus paging counters. */
public final class FangguoRunsListResponse {
    private final List<FangguoRun> runs;
    private final int total;
    private final int limit;
    private final int offset;

    public FangguoRunsListResponse(List<FangguoRun> runs, int total, int limit, int offset) {
        this.runs = runs;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
    }

    public List<FangguoRun> runs() { return runs; }
    public int total()             { return total; }
    public int limit()             { return limit; }
    public int offset()            { return offset; }

    public static FangguoRunsListResponse fromJson(Map<String, Object> o) {
        List<FangguoRun> runs = new ArrayList<>();
        for (Object raw : Json.list(o, "runs")) {
            runs.add(FangguoRun.fromJson(Json.asMap(raw)));
        }
        return new FangguoRunsListResponse(
                Collections.unmodifiableList(runs),
                Json.integ(o, "total"),
                Json.integ(o, "limit"),
                Json.integ(o, "offset"));
    }
}
