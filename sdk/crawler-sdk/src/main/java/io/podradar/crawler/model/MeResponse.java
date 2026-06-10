package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Response of {@code GET /api/v1/me}. {@code scopes} is always {@code ["admin"]} for crawler keys. */
public final class MeResponse {
    private final String name;
    private final List<String> scopes;

    public MeResponse(String name, List<String> scopes) {
        this.name = name;
        this.scopes = scopes;
    }

    public String name()           { return name; }
    public List<String> scopes()   { return scopes; }

    public static MeResponse fromJson(Map<String, Object> o) {
        return new MeResponse(
                Json.str(o, "name"),
                o.containsKey("scopes") ? Json.strList(o, "scopes") : Collections.emptyList());
    }
}
