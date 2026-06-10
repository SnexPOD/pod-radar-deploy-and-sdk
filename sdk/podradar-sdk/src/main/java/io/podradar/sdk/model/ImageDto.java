package io.podradar.sdk.model;

import io.podradar.sdk.internal.Json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Full image record returned by {@code getImage} / inside upload responses. */
public final class ImageDto {
    private final long imageId;
    private final String source;
    private final String sourceId;
    private final String url;
    private final String title;
    private final Integer width;
    private final Integer height;
    private final Long bytes;
    private final String mime;
    private final String thumb;
    private final String full;
    private final String status;
    private final Long duplicateOf;
    private final List<String> tags;
    private final String fetchedAt;
    private final String createdAt;

    private ImageDto(long imageId, String source, String sourceId, String url, String title,
                     Integer width, Integer height, Long bytes, String mime, String thumb,
                     String full, String status, Long duplicateOf, List<String> tags,
                     String fetchedAt, String createdAt) {
        this.imageId = imageId;
        this.source = source;
        this.sourceId = sourceId;
        this.url = url;
        this.title = title;
        this.width = width;
        this.height = height;
        this.bytes = bytes;
        this.mime = mime;
        this.thumb = thumb;
        this.full = full;
        this.status = status;
        this.duplicateOf = duplicateOf;
        this.tags = tags;
        this.fetchedAt = fetchedAt;
        this.createdAt = createdAt;
    }

    public long imageId() { return imageId; }
    public String source() { return source; }
    public String sourceId() { return sourceId; }
    public String url() { return url; }
    public String title() { return title; }
    public Integer width() { return width; }
    public Integer height() { return height; }
    public Long bytes() { return bytes; }
    public String mime() { return mime; }
    public String thumb() { return thumb; }
    public String full() { return full; }
    public String status() { return status; }
    public Long duplicateOf() { return duplicateOf; }
    public List<String> tags() { return tags; }
    public String fetchedAt() { return fetchedAt; }
    public String createdAt() { return createdAt; }

    public static ImageDto fromJson(Map<String, Object> o) {
        return new ImageDto(
                Json.lng(o, "id"),
                Json.str(o, "source"),
                Json.str(o, "source_id"),
                Json.str(o, "url"),
                Json.str(o, "title"),
                o.get("width") instanceof Number ? ((Number) o.get("width")).intValue() : null,
                o.get("height") instanceof Number ? ((Number) o.get("height")).intValue() : null,
                o.get("bytes") instanceof Number ? ((Number) o.get("bytes")).longValue() : null,
                Json.str(o, "mime"),
                Json.str(o, "thumb"),
                Json.str(o, "full"),
                Json.str(o, "status"),
                o.get("duplicate_of") instanceof Number ? ((Number) o.get("duplicate_of")).longValue() : null,
                o.containsKey("tags") ? Json.strList(o, "tags") : Collections.emptyList(),
                Json.str(o, "fetched_at"),
                Json.str(o, "created_at"));
    }
}
