package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * One fangguo image asset (effect/production/finished) attached to an order unit. {@code thumb}
 * and {@code full} are the same presigned URL (fangguo images have no separate thumbnail), and are
 * {@code null} until the asset reaches {@code fetched}. {@code id}/{@code width}/{@code height} are
 * {@code null} before the bytes are harvested.
 */
public final class FangguoAsset {
    private final Long id;
    private final String assetKind;
    private final String status;
    private final String externalKey;
    private final String url;
    private final String lastError;
    private final Integer width;
    private final Integer height;
    private final String thumb;
    private final String full;

    public FangguoAsset(Long id, String assetKind, String status, String externalKey, String url,
                        String lastError, Integer width, Integer height, String thumb, String full) {
        this.id = id;
        this.assetKind = assetKind;
        this.status = status;
        this.externalKey = externalKey;
        this.url = url;
        this.lastError = lastError;
        this.width = width;
        this.height = height;
        this.thumb = thumb;
        this.full = full;
    }

    public Long id()            { return id; }
    public String assetKind()   { return assetKind; }
    public String status()      { return status; }
    public String externalKey() { return externalKey; }
    public String url()         { return url; }
    public String lastError()   { return lastError; }
    public Integer width()      { return width; }
    public Integer height()     { return height; }
    public String thumb()       { return thumb; }
    public String full()        { return full; }

    public static FangguoAsset fromJson(Map<String, Object> o) {
        return new FangguoAsset(
                nullableLong(o.get("id")),
                Json.str(o, "asset_kind"),
                Json.str(o, "status"),
                Json.str(o, "external_key"),
                Json.str(o, "url"),
                Json.str(o, "last_error"),
                nullableInt(o.get("width")),
                nullableInt(o.get("height")),
                Json.str(o, "thumb"),
                Json.str(o, "full"));
    }

    private static Long nullableLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    private static Integer nullableInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
