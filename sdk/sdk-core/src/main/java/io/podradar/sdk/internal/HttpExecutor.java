package io.podradar.sdk.internal;

import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.error.PodRadarNetworkException;
import io.podradar.sdk.error.PodRadarServerException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Thin wrapper around {@link java.net.HttpURLConnection} (Java 8 compatible; zero
 * runtime dependencies). Adds X-API-Key + User-Agent, unwraps non-2xx responses via
 * {@link HttpErrorMapper}, and optionally retries on {@link PodRadarServerException}
 * with exponential backoff.
 *
 * <p>Async variants run the same blocking pipeline (including retries/backoff) on a
 * lazily-created daemon thread pool; call {@link #close()} to shut it down eagerly.
 */
public final class HttpExecutor {
    private final SdkConfig cfg;
    private volatile ExecutorService asyncPool;

    public HttpExecutor(SdkConfig cfg) {
        this.cfg = cfg;
    }

    public String getJson(String path) {
        return execute(new Request(path, "GET", null, null, true));
    }

    public String postJson(String path, String body) {
        return execute(jsonRequest(path, "POST", body));
    }

    public String putJson(String path, String body) {
        return execute(jsonRequest(path, "PUT", body));
    }

    public String deleteJson(String path) {
        return execute(new Request(path, "DELETE", null, null, true));
    }

    public String postMultipart(String path, Multipart form) {
        return execute(new Request(path, "POST", form.body(), form.contentType(), false));
    }

    public CompletableFuture<String> getJsonAsync(String path) {
        return executeAsync(new Request(path, "GET", null, null, true));
    }

    public CompletableFuture<String> postJsonAsync(String path, String body) {
        return executeAsync(jsonRequest(path, "POST", body));
    }

    public CompletableFuture<String> putJsonAsync(String path, String body) {
        return executeAsync(jsonRequest(path, "PUT", body));
    }

    public CompletableFuture<String> deleteJsonAsync(String path) {
        return executeAsync(new Request(path, "DELETE", null, null, true));
    }

    public CompletableFuture<String> postMultipartAsync(String path, Multipart form) {
        return executeAsync(new Request(path, "POST", form.body(), form.contentType(), false));
    }

    private Request jsonRequest(String path, String method, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        String contentType = body == null ? null : "application/json; charset=utf-8";
        return new Request(path, method, bytes, contentType, true);
    }

    /** Immutable request descriptor (replayable across retries, unlike a live connection). */
    private static final class Request {
        final String path;
        final String method;
        final byte[] body;
        final String contentType;
        final boolean acceptJson;

        Request(String path, String method, byte[] body, String contentType, boolean acceptJson) {
            this.path = path;
            this.method = method;
            this.body = body;
            this.contentType = contentType;
            this.acceptJson = acceptJson;
        }
    }

    private static URI resolve(URI base, String path) {
        String b = base.toString();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (path == null || path.isEmpty()) return URI.create(b);
        if (!path.startsWith("/")) path = "/" + path;
        return URI.create(b + path);
    }

    private String execute(Request req) {
        int attempts = cfg.retryOnServerError() ? cfg.maxRetries() + 1 : 1;
        PodRadarException lastServerException = null;
        for (int i = 0; i < attempts; i++) {
            try {
                Response resp = send(req);
                if (resp.status >= 200 && resp.status < 300) {
                    return resp.body;
                }
                PodRadarException ex = HttpErrorMapper.map(resp.status, resp.headers, resp.body);
                if (ex instanceof PodRadarServerException && i < attempts - 1) {
                    lastServerException = ex;
                    sleepBackoff(i);
                    continue;
                }
                throw ex;
            } catch (IOException e) {
                // Thread interruption surfaces as InterruptedIOException here (SocketTimeout-
                // Exception is its subclass but means a timeout, not cancellation) — abort
                // immediately instead of burning the retry budget, mirroring the old
                // java.net.http behavior of failing fast on InterruptedException.
                if (Thread.currentThread().isInterrupted()
                        || (e instanceof InterruptedIOException && !(e instanceof SocketTimeoutException))) {
                    Thread.currentThread().interrupt();
                    throw new PodRadarNetworkException("interrupted", e);
                }
                if (i < attempts - 1) {
                    sleepBackoff(i);
                    continue;
                }
                throw new PodRadarNetworkException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }
        throw lastServerException == null
                ? new PodRadarNetworkException("exhausted retries", null)
                : lastServerException;
    }

    private CompletableFuture<String> executeAsync(Request req) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            pool().execute(() -> {
                try {
                    future.complete(execute(req));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            // close() raced this submission — fail the future instead of throwing
            // synchronously, so async callers always get their error via the future.
            future.completeExceptionally(new PodRadarNetworkException("client closed", e));
        }
        return future;
    }

    /** One blocking HTTP round-trip; status/headers/body fully read before returning. */
    private Response send(Request req) throws IOException {
        URL url = new URL(resolve(cfg.endpoint(), req.path).toString());
        // Not calling disconnect() afterwards keeps the underlying connection eligible
        // for the JDK's keep-alive pool; all streams are fully read + closed below.
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(req.method);
        conn.setConnectTimeout((int) Math.min(cfg.connectTimeout().toMillis(), Integer.MAX_VALUE));
        // setReadTimeout caps each blocking read; the whole-exchange bound (the old
        // java.net.http HttpRequest.timeout semantics) is enforced by the deadline
        // check in readAll() — a slow-dripping server cannot stretch a request far
        // beyond cfg.requestTimeout().
        conn.setReadTimeout((int) Math.min(cfg.requestTimeout().toMillis(), Integer.MAX_VALUE));
        long deadlineNanos = System.nanoTime() + cfg.requestTimeout().toNanos();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("X-API-Key", cfg.apiKey());
        conn.setRequestProperty("User-Agent", cfg.userAgent());
        if (req.acceptJson) {
            conn.setRequestProperty("Accept", "application/json");
        }
        if (req.body != null) {
            if (req.contentType != null) {
                conn.setRequestProperty("Content-Type", req.contentType);
            }
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(req.body.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(req.body);
            }
        }

        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body = in == null ? "" : readAll(in, deadlineNanos);
        Map<String, List<String>> headers = conn.getHeaderFields();
        return new Response(status, headers == null ? Collections.emptyMap() : headers, body);
    }

    private static String readAll(InputStream in, long deadlineNanos) throws IOException {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
                if (System.nanoTime() - deadlineNanos > 0) {
                    throw new SocketTimeoutException("request timeout exceeded while reading response body");
                }
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static final class Response {
        final int status;
        final Map<String, List<String>> headers;
        final String body;

        Response(int status, Map<String, List<String>> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }
    }

    private ExecutorService pool() {
        ExecutorService p = asyncPool;
        if (p == null || p.isShutdown()) {
            synchronized (this) {
                // Recreate after close(): the executor may be shared across a sync
                // client and async wrappers (wrap()), so closing one must not
                // permanently break the others — mirrors the old no-op close().
                if (asyncPool == null || asyncPool.isShutdown()) {
                    asyncPool = Executors.newCachedThreadPool(r -> {
                        Thread t = new Thread(r, "podradar-sdk-async");
                        t.setDaemon(true);
                        return t;
                    });
                }
                p = asyncPool;
            }
        }
        return p;
    }

    /**
     * Releases the async pool's threads if one was ever created. Sync calls remain
     * usable, and a later async call lazily recreates the pool (threads are daemon,
     * so even an un-closed executor never blocks JVM exit).
     */
    public void close() {
        ExecutorService p = asyncPool;
        if (p != null) {
            p.shutdown();
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(backoffMillis(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long backoffMillis(int attempt) {
        long base = 200L;
        long capped = Math.min(base * (1L << Math.min(attempt, 6)), 5000L);
        long jitter = (long) (Math.random() * 100);
        return capped + jitter;
    }

    /** Returns the effective config (read-only). */
    public SdkConfig config() {
        return cfg;
    }

    static URI _resolveForTest(URI base, String path) {
        return resolve(base, path);
    }
}
