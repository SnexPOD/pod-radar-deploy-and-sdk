package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * Response of {@code POST /api/v1/fangguo/runs}: the freshly created run's id and its initial
 * {@code status} (always {@code "running"}). A 409 with an already-running run id is surfaced as a
 * thrown {@code PodRadarConflictException} rather than this DTO.
 */
public final class FangguoRunResponse {
    private final long runId;
    private final String status;

    public FangguoRunResponse(long runId, String status) {
        this.runId = runId;
        this.status = status;
    }

    public long runId()    { return runId; }
    public String status() { return status; }

    public static FangguoRunResponse fromJson(Map<String, Object> o) {
        return new FangguoRunResponse(
                Json.lng(o, "run_id"),
                Json.str(o, "status"));
    }
}
