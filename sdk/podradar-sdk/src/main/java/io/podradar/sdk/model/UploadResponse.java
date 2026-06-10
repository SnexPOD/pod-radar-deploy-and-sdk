package io.podradar.sdk.model;

import io.podradar.sdk.internal.Json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Response of {@code POST /api/v1/images}. */
public final class UploadResponse {
    private final String jobId;
    private final ImageDto image;
    private final boolean created;
    private final int queuedCount;
    private final List<String> queuedModels;

    private UploadResponse(String jobId, ImageDto image, boolean created, int queuedCount, List<String> queuedModels) {
        this.jobId = jobId;
        this.image = image;
        this.created = created;
        this.queuedCount = queuedCount;
        this.queuedModels = queuedModels;
    }

    public String jobId() { return jobId; }
    public ImageDto image() { return image; }
    public boolean created() { return created; }
    public int queuedCount() { return queuedCount; }
    public List<String> queuedModels() { return queuedModels; }

    public static UploadResponse fromJson(Map<String, Object> o) {
        return new UploadResponse(
                Json.str(o, "job_id"),
                ImageDto.fromJson(Json.obj(o, "image")),
                Json.bool(o, "created"),
                Json.integ(o, "queued_count"),
                o.containsKey("queued_models") ? Json.strList(o, "queued_models") : Collections.emptyList());
    }
}
