package io.podradar.sdk;

import io.podradar.sdk.internal.HttpExecutor;
import io.podradar.sdk.internal.JsonReader;
import io.podradar.sdk.internal.JsonWriter;
import io.podradar.sdk.internal.Multipart;
import io.podradar.sdk.internal.SdkConfig;
import io.podradar.sdk.internal.Urls;
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
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async sibling of {@link PodRadarClient}. Every method returns a
 * {@link CompletableFuture} that completes with the parsed response or
 * fails with a {@code PodRadarException} subclass.
 */
public final class PodRadarAsyncClient implements AutoCloseable {
    private static final String API = "/api/v1";

    private final HttpExecutor http;

    private PodRadarAsyncClient(HttpExecutor http) {
        this.http = http;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Wrap an existing sync client so both share the same {@link HttpExecutor}. */
    public static PodRadarAsyncClient wrap(PodRadarClient sync) {
        return new PodRadarAsyncClient(sync.http());
    }

    // ───── 5.1 Search ─────────────────────────────────────────────────

    public CompletableFuture<SearchResponse> search(SearchRequest req) {
        // POST /search reads k / min_score from the query string for every input
        // mode (the JSON body schema only accepts text/image_url), so they
        // must go on the URL — not in the multipart form or JSON body.
        String path = API + "/search" + searchQuery(req);
        CompletableFuture<String> raw;
        if (req.isMultipart()) {
            Multipart form;
            try {
                form = buildSearchMultipart(req);
            } catch (RuntimeException ex) {
                return failed(ex);
            }
            raw = http.postMultipartAsync(path, form);
        } else {
            Map<String, Object> json = new LinkedHashMap<>();
            switch (req.mode()) {
                case URL:      json.put("image_url", req.url().toString()); break;
                case TEXT:     /* handled below */ break;
                default: return failed(new IllegalStateException("unreachable mode " + req.mode()));
            }
            if (req.text() != null) json.put("text", req.text());
            raw = http.postJsonAsync(path, JsonWriter.write(json));
        }
        return raw.thenApply(body -> SearchResponse.fromJson(JsonReader.parseObject(body)));
    }

    /** k / min_score live in the query string for POST /search (all input modes). */
    private static String searchQuery(SearchRequest req) {
        StringBuilder qs = new StringBuilder("?k=").append(req.k());
        if (req.minScore() != null) qs.append("&min_score=").append(req.minScore());
        return qs.toString();
    }

    // ───── 5.2 Batch search ───────────────────────────────────────────

    public CompletableFuture<BatchSearchAsyncResponse> searchBatch(BatchSearchRequest req) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("urls", urisToStrings(req.urls()));
        json.put("k", req.k());
        json.put("min_score", req.minScore());
        return http.postJsonAsync(API + "/search/batch", JsonWriter.write(json))
                .thenApply(body -> BatchSearchAsyncResponse.fromJson(JsonReader.parseObject(body)));
    }

    public CompletableFuture<SearchJobStatus> getSearchJob(long searchJobId) {
        return http.getJsonAsync(API + "/search/jobs/" + searchJobId)
                .thenApply(body -> SearchJobStatus.fromJson(JsonReader.parseObject(body)));
    }

    public CompletableFuture<SearchJobItemsResponse> listSearchJobItems(long searchJobId, PageQuery page) {
        String path = API + "/search/jobs/" + searchJobId + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path)
                .thenApply(body -> SearchJobItemsResponse.fromJson(JsonReader.parseObject(body)));
    }

    // ───── 5.3 Upload ─────────────────────────────────────────────────

    public CompletableFuture<UploadResponse> upload(UploadRequest req) {
        CompletableFuture<String> raw;
        if (req.isMultipart()) {
            Multipart form;
            try {
                form = buildUploadMultipart(req);
            } catch (RuntimeException ex) {
                return failed(ex);
            }
            raw = http.postMultipartAsync(API + "/images", form);
        } else {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("image_url", req.url().toString());
            if (req.source() != null) json.put("source", req.source());
            if (req.sourceId() != null) json.put("source_id", req.sourceId());
            if (req.title() != null) json.put("title", req.title());
            if (req.tags() != null) json.put("tags", req.tags());
            if (req.meta() != null) json.put("meta", req.meta());
            raw = http.postJsonAsync(API + "/images", JsonWriter.write(json));
        }
        return raw.thenApply(body -> UploadResponse.fromJson(JsonReader.parseObject(body)));
    }

    // ───── 5.4 Batch upload ───────────────────────────────────────────

    public CompletableFuture<BatchUploadResponse> uploadBatch(BatchUploadRequest req) {
        List<Map<String, Object>> items = new ArrayList<>();
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
        return http.postJsonAsync(API + "/images/batch", JsonWriter.write(json))
                .thenApply(body -> BatchUploadResponse.fromJson(JsonReader.parseObject(body)));
    }

    // ───── 5.5 Job paging / retry ─────────────────────────────────────

    public CompletableFuture<WriteJobItemsResponse> listJobItems(String jobId, PageQuery page) {
        String path = API + "/images/jobs/" + urlEncode(jobId) + "/items"
                + "?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path)
                .thenApply(body -> WriteJobItemsResponse.fromJson(JsonReader.parseObject(body)));
    }

    public CompletableFuture<BatchUploadResponse> retryFailedJob(String jobId) {
        return http.postJsonAsync(API + "/images/jobs/" + urlEncode(jobId) + "/retry-failed", "{}")
                .thenApply(body -> BatchUploadResponse.fromJson(JsonReader.parseObject(body)));
    }

    // ───── 5.6 Single image read / list ───────────────────────────────

    public CompletableFuture<ImageDto> getImage(long imageId) {
        return http.getJsonAsync(API + "/image/" + imageId)
                .thenApply(body -> ImageDto.fromJson(JsonReader.parseObject(body)));
    }

    public CompletableFuture<ImagesListResponse> listImages(PageQuery page) {
        String path = API + "/images?limit=" + page.limit() + "&offset=" + page.offset();
        return http.getJsonAsync(path)
                .thenApply(body -> ImagesListResponse.fromJson(JsonReader.parseObject(body)));
    }

    // ───── helpers ────────────────────────────────────────────────────

    private static Multipart buildSearchMultipart(SearchRequest req) {
        Multipart form = new Multipart();
        attachImage(form, req);
        if (req.text() != null) form.addField("text", req.text());
        return form;
    }

    private static Multipart buildUploadMultipart(UploadRequest req) {
        Multipart form = new Multipart();
        attachUploadImage(form, req);
        if (req.source() != null) form.addField("source", req.source());
        if (req.sourceId() != null) form.addField("source_id", req.sourceId());
        if (req.title() != null) form.addField("title", req.title());
        if (req.tags() != null && !req.tags().isEmpty()) {
            form.addField("tags", String.join(",", req.tags()));
        }
        if (req.meta() != null) form.addField("meta", JsonWriter.write(req.meta()));
        return form;
    }

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

    private static List<String> urisToStrings(List<URI> urls) {
        List<String> out = new ArrayList<>(urls.size());
        for (URI u : urls) out.add(u.toString());
        return out;
    }

    private static String urlEncode(String s) {
        return Urls.encode(s);
    }

    private static <T> CompletableFuture<T> failed(Throwable t) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }

    /** Returns the read-only effective config. */
    public SdkConfig config() { return http.config(); }

    @Override
    public void close() {
        http.close(); // shuts down the async pool
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

        public PodRadarAsyncClient build() {
            return new PodRadarAsyncClient(new HttpExecutor(cfg.build()));
        }
    }
}
