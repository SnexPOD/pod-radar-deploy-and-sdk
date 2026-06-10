package io.podradar.sdk.model;

import java.io.File;
import java.net.URI;
import java.util.Objects;

/**
 * Builder for {@code POST /api/v1/search}. Use one of the four factory methods
 * ({@link #fromFile}/{@link #fromBytes}/{@link #fromUrl}/{@link #fromText})
 * then chain {@code .withText/.withMinScore/.withRequestId} as needed.
 */
public final class SearchRequest {

    public enum Mode { FILE, BYTES, URL, TEXT }

    private final Mode mode;
    private final File file;
    private final byte[] bytes;
    private final String bytesMime;
    private final URI url;
    private final int k;
    private String text;
    private Double minScore;
    private String requestId;

    private SearchRequest(Mode mode, File file, byte[] bytes, String bytesMime,
                          URI url, String text, int k) {
        if (k < 1 || k > 100) throw new IllegalArgumentException("k must be in [1, 100], got " + k);
        this.mode = mode;
        this.file = file;
        this.bytes = bytes;
        this.bytesMime = bytesMime;
        this.url = url;
        this.text = text;
        this.k = k;
    }

    public static SearchRequest fromFile(File file, int k) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("file must exist: " + file);
        }
        return new SearchRequest(Mode.FILE, file, null, null, null, null, k);
    }

    public static SearchRequest fromBytes(byte[] data, String mime, int k) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) throw new IllegalArgumentException("data is empty");
        Objects.requireNonNull(mime, "mime");
        return new SearchRequest(Mode.BYTES, null, data, mime, null, null, k);
    }

    public static SearchRequest fromUrl(URI imageUrl, int k) {
        Objects.requireNonNull(imageUrl, "imageUrl");
        return new SearchRequest(Mode.URL, null, null, null, imageUrl, null, k);
    }

    public static SearchRequest fromText(String text, int k) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("text is empty");
        return new SearchRequest(Mode.TEXT, null, null, null, null, text, k);
    }

    public SearchRequest withText(String text) {
        if (mode == Mode.TEXT) throw new IllegalStateException("already a text-only query");
        this.text = text;
        return this;
    }

    public SearchRequest withMinScore(double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0, 1], got " + minScore);
        }
        this.minScore = minScore;
        return this;
    }

    public SearchRequest withRequestId(String id) {
        this.requestId = id;
        return this;
    }

    public Mode mode() { return mode; }
    public File file() { return file; }
    public byte[] bytes() { return bytes; }
    public String bytesMime() { return bytesMime; }
    public URI url() { return url; }
    public int k() { return k; }
    public String text() { return text; }
    public Double minScore() { return minScore; }
    public String requestId() { return requestId; }

    /** True if the request must be sent as multipart/form-data (binary body present). */
    public boolean isMultipart() {
        return mode == Mode.FILE || mode == Mode.BYTES;
    }
}
