package io.podradar.sdk.internal;

import io.podradar.sdk.error.PodRadarAuthException;
import io.podradar.sdk.error.PodRadarConflictException;
import io.podradar.sdk.error.PodRadarException;
import io.podradar.sdk.error.PodRadarNotFoundException;
import io.podradar.sdk.error.PodRadarRateLimitException;
import io.podradar.sdk.error.PodRadarServerException;
import io.podradar.sdk.error.PodRadarValidationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a non-2xx HTTP response to a {@link PodRadarException} subclass.
 *
 * <p>Body is expected to be JSON shaped {@code {"error": "<msg>", "request_id": "<id>",
 * "details": {...}}}, but we tolerate non-JSON bodies (use the raw text as the error
 * message).
 */
public final class HttpErrorMapper {
    private HttpErrorMapper() {}

    public static PodRadarException map(int statusCode, Map<String, List<String>> headers, String body) {
        String error;
        String requestId;
        Map<String, Object> details;
        try {
            Object parsed = body == null || body.isEmpty() ? null : JsonReader.parse(body);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> obj = (Map<String, Object>) parsed;
                error = stringOr(obj.get("error"), "HTTP " + statusCode);
                requestId = stringOr(obj.get("request_id"), null);
                Object d = obj.get("details");
                details = (d instanceof Map) ? coerceMap(d) : Collections.emptyMap();
            } else {
                error = (body == null || body.isEmpty()) ? "HTTP " + statusCode : body;
                requestId = null;
                details = Collections.emptyMap();
            }
        } catch (RuntimeException e) {
            error = (body == null || body.isEmpty()) ? "HTTP " + statusCode : body;
            requestId = null;
            details = Collections.emptyMap();
        }

        switch (statusCode) {
            case 400:
            case 413:
            case 415:
                return new PodRadarValidationException(statusCode, error, requestId, details);
            case 401:
            case 403:
                return new PodRadarAuthException(statusCode, error, requestId);
            case 404:
                return new PodRadarNotFoundException(error, requestId);
            case 409:
                return new PodRadarConflictException(error, requestId);
            case 429: {
                long retryAfter = parseRetryAfter(headers);
                return new PodRadarRateLimitException(error, requestId, retryAfter);
            }
            default:
                if (statusCode >= 500 && statusCode < 600) {
                    return new PodRadarServerException(statusCode, error, requestId);
                }
                return new PodRadarException(statusCode, error, requestId);
        }
    }

    private static long parseRetryAfter(Map<String, List<String>> headers) {
        if (headers == null) return 0L;
        // HttpURLConnection's getHeaderFields() preserves wire casing — match case-insensitively.
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() == null || !"Retry-After".equalsIgnoreCase(e.getKey())) continue;
            List<String> values = e.getValue();
            if (values == null || values.isEmpty() || values.get(0) == null) return 0L;
            try {
                return Long.parseLong(values.get(0).trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String stringOr(Object v, String fallback) {
        return v instanceof String ? (String) v : fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> coerceMap(Object v) {
        Map<String, Object> out = new LinkedHashMap<>();
        ((Map<Object, Object>) v).forEach((k, val) -> out.put(String.valueOf(k), val));
        return out;
    }
}
