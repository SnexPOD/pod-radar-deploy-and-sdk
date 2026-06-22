package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.Map;

/**
 * The {@code order} sub-block on a {@link FangguoItem}: shop/platform context for the order the
 * unit belongs to. All fields are strings as returned by the server ({@code hasPackage} too —
 * it is the raw textual flag, not a boolean).
 */
public final class FangguoOrderInfo {
    private final String shipStatus;
    private final String storeName;
    private final String shopName;
    private final String platformDesc;
    private final String tradeId;
    private final String hasPackage;

    public FangguoOrderInfo(String shipStatus, String storeName, String shopName,
                            String platformDesc, String tradeId, String hasPackage) {
        this.shipStatus = shipStatus;
        this.storeName = storeName;
        this.shopName = shopName;
        this.platformDesc = platformDesc;
        this.tradeId = tradeId;
        this.hasPackage = hasPackage;
    }

    public String shipStatus()   { return shipStatus; }
    public String storeName()    { return storeName; }
    public String shopName()     { return shopName; }
    public String platformDesc() { return platformDesc; }
    public String tradeId()      { return tradeId; }
    public String hasPackage()   { return hasPackage; }

    public static FangguoOrderInfo fromJson(Map<String, Object> o) {
        return new FangguoOrderInfo(
                Json.str(o, "ship_status"),
                Json.str(o, "store_name"),
                Json.str(o, "shop_name"),
                Json.str(o, "platform_desc"),
                Json.str(o, "trade_id"),
                Json.str(o, "has_package"));
    }
}
