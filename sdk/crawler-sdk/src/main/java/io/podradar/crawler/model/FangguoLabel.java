package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.List;
import java.util.Map;

/**
 * One fangguo shipping label attached to an order. {@code image} is the rendered label image and
 * {@code pages} the per-page images (both presigned, {@code null}/empty until fetched); {@code pdf}
 * is the presigned PDF. {@code id}/{@code pageCount} are {@code null} before the label is harvested.
 */
public final class FangguoLabel {
    private final Long id;
    private final String packageNo;
    private final String carrier;
    private final String status;
    private final List<String> trackNumbers;
    private final String shippingUrlPath;
    private final Integer pageCount;
    private final String lastError;
    private final String image;
    private final List<String> pages;
    private final String pdf;

    public FangguoLabel(Long id, String packageNo, String carrier, String status,
                        List<String> trackNumbers, String shippingUrlPath, Integer pageCount,
                        String lastError, String image, List<String> pages, String pdf) {
        this.id = id;
        this.packageNo = packageNo;
        this.carrier = carrier;
        this.status = status;
        this.trackNumbers = trackNumbers;
        this.shippingUrlPath = shippingUrlPath;
        this.pageCount = pageCount;
        this.lastError = lastError;
        this.image = image;
        this.pages = pages;
        this.pdf = pdf;
    }

    public Long id()                   { return id; }
    public String packageNo()          { return packageNo; }
    public String carrier()            { return carrier; }
    public String status()             { return status; }
    public List<String> trackNumbers() { return trackNumbers; }
    public String shippingUrlPath()    { return shippingUrlPath; }
    public Integer pageCount()         { return pageCount; }
    public String lastError()          { return lastError; }
    public String image()              { return image; }
    public List<String> pages()        { return pages; }
    public String pdf()                { return pdf; }

    public static FangguoLabel fromJson(Map<String, Object> o) {
        return new FangguoLabel(
                nullableLong(o.get("id")),
                Json.str(o, "package_no"),
                Json.str(o, "carrier"),
                Json.str(o, "status"),
                Json.strList(o, "track_numbers"),
                Json.str(o, "shipping_url_path"),
                nullableInt(o.get("page_count")),
                Json.str(o, "last_error"),
                Json.str(o, "image"),
                Json.strList(o, "pages"),
                Json.str(o, "pdf"));
    }

    private static Long nullableLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    private static Integer nullableInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
