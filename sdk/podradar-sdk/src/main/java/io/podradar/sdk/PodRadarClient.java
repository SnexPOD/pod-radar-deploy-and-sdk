package io.podradar.sdk;

import io.podradar.sdk.internal.HttpExecutor;
import io.podradar.sdk.internal.Json;
import io.podradar.sdk.internal.JsonReader;
import io.podradar.sdk.internal.JsonWriter;
import io.podradar.sdk.internal.Multipart;
import io.podradar.sdk.internal.SdkConfig;
import io.podradar.sdk.model.BatchSearchAsyncResponse;
import io.podradar.sdk.model.BatchSearchRequest;
import io.podradar.sdk.model.BatchUploadRequest;
import io.podradar.sdk.model.BatchUploadResponse;
import io.podradar.sdk.model.ImageDto;
import io.podradar.sdk.model.ImagesListResponse;
import io.podradar.sdk.model.PageQuery;
import io.podradar.sdk.model.SearchJobItemsResponse;
import io.podradar.sdk.model.SearchJobStatus;
import io.podradar.sdk.model.SearchRequest;
import io.podradar.sdk.model.SearchResponse;
import io.podradar.sdk.model.UploadRequest;
import io.podradar.sdk.model.UploadResponse;
import io.podradar.sdk.model.WriteJobItemsResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronous client for the pod-radar main system.
 *
 * <p>Construct via {@link #builder()}. Methods throw subclasses of
 * {@code PodRadarException} on non-2xx responses.
 */
public final class PodRadarClient implements AutoCloseable {
    private static final String API = "/api/v1";

    private final HttpExecutor http;

    private PodRadarClient(HttpExecutor http) {
        this.http = http;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ───── 5.1 Search ─────────────────────────────────────────────────

    public SearchResponse search(SearchRequest req) {
        // POST /search reads k / min_score from the query string for every input
        // mode (the JSON body schema only accepts text/image_url), so they
        // must go on the URL — not in the multipart form or JSON body.
        String path = API + "/search" + searchQuery(req);
        String body;
        if (req.isMultipart()) {
            Multipart form = new Multipart();
            attachImage(form, req);
            if (req.text() != null) form.addField("text", req.text());
            body = http.postMultipart(path, form);
        } else {
            Map<String, Object> json = new LinkedHashMap<>();
            switch (req.mode()) {
                case URL:      json.put("image_url", req.url().toString()); break;
                case TEXT:     /* handled below */ break;
                default: throw new IllegalStateException("unreachable mode " + req.mode());
            }
            if (req.text() != null) json.put("text", req.text());
            body = http.postJson(path, JsonWriter.write(json));
        }
        return SearchResponse.fromJson(JsonReader.parseObject(body));
    }

    /** k / min_score live in the query string for POST /search (all input modes). */
    private static String searchQuery(SearchRequest req) {
        StringBuilder qs = new StringBuilder("?k=").append(req.k());
        if (req.minScore() != null) qs.append("&min_score=").append(req.minScore());
        return qs.toString();
    }

    // ───── 5.2 Batch search ───────────────────────────────────────────

    public BatchSearchAsyncResponse searchBatch(BatchSearchRequest req) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("urls", urisToStrings(req.urls()));
        json.put("k", req.k());
        json.put("min_score", req.minScore());
        String body = http.postJson(API + "/search/batch", JsonWriter.write(json));
        return BatchSearchAsyncResponse.fromJson(JsonReader.parseObject(body));
    }

    public SearchJobStatus getSearchJob(long searchJobId) {
        String body = http.getJson(API + "/search/jobs/" + searchJobId);
        return SearchJobStatus.fromJson(JsonReader.parseObject(body));
    }

    public SearchJobItemsResponse listSearchJobItems(long searchJobId, PageQuery page) {
        String path = API + "/search/jobs/" + searchJobId + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return SearchJobItemsResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    // ───── 5.3 Upload ─────────────────────────────────────────────────

    public UploadResponse upload(UploadRequest req) {
        String body;
        if (req.isMultipart()) {
            Multipart form = new Multipart();
            attachUploadImage(form, req);
            if (req.source() != null) form.addField("source", req.source());
            if (req.sourceId() != null) form.addField("source_id", req.sourceId());
            if (req.title() != null) form.addField("title", req.title());
            if (req.tags() != null && !req.tags().isEmpty()) {
                form.addField("tags", String.join(",", req.tags()));
            }
            if (req.meta() != null) form.addField("meta", JsonWriter.write(req.meta()));
            body = http.postMultipart(API + "/images", form);
        } else {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("image_url", req.url().toString());
            if (req.source() != null) json.put("source", req.source());
            if (req.sourceId() != null) json.put("source_id", req.sourceId());
            if (req.title() != null) json.put("title", req.title());
            if (req.tags() != null) json.put("tags", req.tags());
            if (req.meta() != null) json.put("meta", req.meta());
            body = http.postJson(API + "/images", JsonWriter.write(json));
        }
        return UploadResponse.fromJson(JsonReader.parseObject(body));
    }

    // ───── 5.4 Batch upload ───────────────────────────────────────────

    public BatchUploadResponse uploadBatch(BatchUploadRequest req) {
        java.util.List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (URI u : req.urls()) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("image_url", u.toString());
            if (req.source() != null) it.put("source", req.source());
            if (req.sourceId() != null) it.put("source_id", req.sourceId());
            if (req.title() != null) it.put("title", req.title());
            items.add(it);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("items", items);
        String body = http.postJson(API + "/images/batch", JsonWriter.write(json));
        return BatchUploadResponse.fromJson(JsonReader.parseObject(body));
    }

    // ───── 5.5 Job paging / retry ─────────────────────────────────────

    public WriteJobItemsResponse listJobItems(String jobId, PageQuery page) {
        String path = API + "/images/jobs/" + urlEncode(jobId) + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return WriteJobItemsResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    public BatchUploadResponse retryFailedJob(String jobId) {
        String body = http.postJson(API + "/images/jobs/" + urlEncode(jobId) + "/retry-failed", "{}");
        return BatchUploadResponse.fromJson(JsonReader.parseObject(body));
    }

    // ───── 5.6 Single image read / list ───────────────────────────────

    public ImageDto getImage(long imageId) {
        String body = http.getJson(API + "/image/" + imageId);
        return ImageDto.fromJson(JsonReader.parseObject(body));
    }

    public ImagesListResponse listImages(PageQuery page) {
        String path = API + "/images?limit=" + page.limit() + "&offset=" + page.offset();
        return ImagesListResponse.fromJson(JsonReader.parseObject(http.getJson(path)));
    }

    // ───── helpers ────────────────────────────────────────────────────

    private static void attachImage(Multipart form, SearchRequest req) {
        switch (req.mode()) {
            case FILE: {
                try {
                    byte[] data = Files.readAllBytes(req.file().toPath());
                    form.addFile("file", req.file().getName(), guessMime(req.file().getName()), data);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                break;
            }
            case BYTES:
                form.addFile("file", "image", req.bytesMime(), req.bytes());
                break;
            default:
                throw new IllegalStateException("unreachable");
        }
    }

    private static void attachUploadImage(Multipart form, UploadRequest req) {
        switch (req.mode()) {
            case FILE: {
                try {
                    byte[] data = Files.readAllBytes(req.file().toPath());
                    form.addFile("file", req.file().getName(), guessMime(req.file().getName()), data);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                break;
            }
            case BYTES:
                form.addFile("file", "image", req.bytesMime(), req.bytes());
                break;
            default:
                throw new IllegalStateException("unreachable");
        }
    }

    private static String guessMime(String filename) {
        String n = filename.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private static java.util.List<String> urisToStrings(java.util.List<URI> urls) {
        java.util.List<String> out = new java.util.ArrayList<>(urls.size());
        for (URI u : urls) out.add(u.toString());
        return out;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always supported", e);
        }
    }

    /** Returns the read-only effective config. */
    public SdkConfig config() { return http.config(); }

    @Override
    public void close() {
        http.close(); // shuts down the async pool if one was ever created
    }

    // ───── Builder ────────────────────────────────────────────────────

    public static final class Builder {
        private final SdkConfig.Builder cfg = SdkConfig.builder();

        public Builder endpoint(String url) { cfg.endpoint(url); return this; }
        public Builder endpoint(URI uri) { cfg.endpoint(uri); return this; }
        public Builder apiKey(String key) { cfg.apiKey(key); return this; }
        public Builder connectTimeout(Duration d) { cfg.connectTimeout(d); return this; }
        public Builder requestTimeout(Duration d) { cfg.requestTimeout(d); return this; }
        public Builder userAgent(String ua) { cfg.userAgent(ua); return this; }
        public Builder retryOnServerError(boolean on) { cfg.retryOnServerError(on); return this; }
        public Builder maxRetries(int n) { cfg.maxRetries(n); return this; }

        public PodRadarClient build() {
            return new PodRadarClient(new HttpExecutor(cfg.build()));
        }
    }

    /** Package-private accessor for {@link PodRadarAsyncClient}. */
    HttpExecutor http() { return http; }

    @SuppressWarnings("unused")
    private static Map<String, Object> __keepImport(Map<String, Object> m) { return Json.asMap(m); }
}
