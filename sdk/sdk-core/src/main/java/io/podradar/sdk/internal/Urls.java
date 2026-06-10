package io.podradar.sdk.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Shared URL helpers for the SDK clients. */
public final class Urls {
    private Urls() {}

    /** UTF-8 {@code application/x-www-form-urlencoded} encoding (Java 8 has no Charset overload). */
    public static String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is always supported", e);
        }
    }
}
