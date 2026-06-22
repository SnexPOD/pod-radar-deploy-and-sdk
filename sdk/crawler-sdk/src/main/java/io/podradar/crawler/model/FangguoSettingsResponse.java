package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/** Response of {@code GET /api/v1/fangguo/settings}: {@code {settings, state}}. */
public final class FangguoSettingsResponse {
    private final FangguoSettings settings;
    private final FangguoSyncState state;

    public FangguoSettingsResponse(FangguoSettings settings, FangguoSyncState state) {
        this.settings = settings;
        this.state = state;
    }

    public FangguoSettings settings() { return settings; }
    public FangguoSyncState state()   { return state; }

    public static FangguoSettingsResponse fromJson(Map<String, Object> o) {
        return new FangguoSettingsResponse(
                FangguoSettings.fromJson(Json.obj(o, "settings")),
                FangguoSyncState.fromJson(Json.obj(o, "state")));
    }
}
