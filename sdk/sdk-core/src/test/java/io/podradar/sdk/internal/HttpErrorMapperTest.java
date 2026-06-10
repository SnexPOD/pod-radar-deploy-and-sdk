package io.podradar.sdk.internal;

import io.podradar.sdk.error.PodRadarAuthException;
import io.podradar.sdk.error.PodRadarConflictException;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.error.PodRadarNotFoundException;
import io.podradar.sdk.error.PodRadarRateLimitException;
import io.podradar.sdk.error.PodRadarServerException;
import io.podradar.sdk.error.PodRadarValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpErrorMapperTest {

    private static Map<String, List<String>> headers(Map<String, List<String>> kv) {
        return kv;
    }

    @Test
    void maps400ToValidation() {
        PodRadarException ex = HttpErrorMapper.map(400, headers(Map.of()),
                "{\"error\":\"bad input\",\"request_id\":\"req-1\",\"details\":{\"field\":\"top_k\"}}");
        assertTrue(ex instanceof PodRadarValidationException);
        assertEquals("bad input", ex.error());
        assertEquals("req-1", ex.requestId());
        assertEquals("top_k", ((PodRadarValidationException) ex).details().get("field"));
    }

    @Test
    void maps413And415ToValidation() {
        assertTrue(HttpErrorMapper.map(413, headers(Map.of()), "{\"error\":\"too big\"}")
                instanceof PodRadarValidationException);
        assertTrue(HttpErrorMapper.map(415, headers(Map.of()), "{\"error\":\"bad type\"}")
                instanceof PodRadarValidationException);
    }

    @Test
    void maps401And403ToAuth() {
        assertTrue(HttpErrorMapper.map(401, headers(Map.of()), "{\"error\":\"no key\"}")
                instanceof PodRadarAuthException);
        assertTrue(HttpErrorMapper.map(403, headers(Map.of()), "{\"error\":\"no scope\"}")
                instanceof PodRadarAuthException);
    }

    @Test
    void maps404ToNotFound() {
        assertTrue(HttpErrorMapper.map(404, headers(Map.of()), "{\"error\":\"gone\"}")
                instanceof PodRadarNotFoundException);
    }

    @Test
    void maps409ToConflict() {
        assertTrue(HttpErrorMapper.map(409, headers(Map.of()), "{\"error\":\"already running\"}")
                instanceof PodRadarConflictException);
    }

    @Test
    void maps429ToRateLimitWithRetryAfter() {
        PodRadarException ex = HttpErrorMapper.map(429,
                headers(Map.of("Retry-After", List.of("30"))),
                "{\"error\":\"slow down\"}");
        assertTrue(ex instanceof PodRadarRateLimitException);
        assertEquals(30L, ((PodRadarRateLimitException) ex).retryAfterSeconds());
    }

    @Test
    void retryAfterHeaderLookupIsCaseInsensitive() {
        // HttpURLConnection's getHeaderFields() preserves wire casing.
        PodRadarException ex = HttpErrorMapper.map(429,
                headers(Map.of("retry-after", List.of("7"))),
                "{\"error\":\"slow down\"}");
        assertEquals(7L, ((PodRadarRateLimitException) ex).retryAfterSeconds());
    }

    @Test
    void maps429WithoutRetryAfterHeaderToZero() {
        PodRadarException ex = HttpErrorMapper.map(429, headers(Map.of()),
                "{\"error\":\"slow down\"}");
        assertEquals(0L, ((PodRadarRateLimitException) ex).retryAfterSeconds());
    }

    @Test
    void maps5xxToServer() {
        assertTrue(HttpErrorMapper.map(500, headers(Map.of()), "{\"error\":\"boom\"}")
                instanceof PodRadarServerException);
        assertTrue(HttpErrorMapper.map(503, headers(Map.of()), "{\"error\":\"down\"}")
                instanceof PodRadarServerException);
    }

    @Test
    void tolerantToNonJsonBody() {
        PodRadarException ex = HttpErrorMapper.map(500, headers(Map.of()), "<html>oops</html>");
        assertTrue(ex instanceof PodRadarServerException);
        assertEquals("<html>oops</html>", ex.error());
    }

    @Test
    void tolerantToEmptyBody() {
        PodRadarException ex = HttpErrorMapper.map(502, headers(Map.of()), "");
        assertTrue(ex instanceof PodRadarServerException);
        assertEquals("HTTP 502", ex.error());
    }
}
