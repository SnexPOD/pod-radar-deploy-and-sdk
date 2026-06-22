package io.podradar.crawler.model;

import io.podradar.sdk.internal.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A fangguo order unit (one SKU line within an order) with its harvested {@code assets} and
 * {@code labels} and an {@code order} context block. Returned by {@code GET /runs/{id}/items} and
 * {@code GET /items}. The identifier columns ({@code oid}, {@code skuId}, {@code coverTaskId},
 * {@code skuProps}) are surfaced as strings exactly as the server emits them.
 */
public final class FangguoItem {
    private final long id;
    private final long orderId;
    private final String tid;
    private final String unitKey;
    private final String barcode;
    private final String coverTaskId;
    private final String oid;
    private final String skuId;
    private final String skuExtCode;
    private final String factoryEncode;
    private final String skuProps;
    private final String coverStatus;
    private final Integer unitIdx;
    private final Integer unitTotal;
    private final String sourceCreatedAt;
    private final FangguoOrderInfo order;
    private final List<FangguoAsset> assets;
    private final List<FangguoLabel> labels;

    public FangguoItem(long id, long orderId, String tid, String unitKey, String barcode,
                       String coverTaskId, String oid, String skuId, String skuExtCode,
                       String factoryEncode, String skuProps, String coverStatus, Integer unitIdx,
                       Integer unitTotal, String sourceCreatedAt, FangguoOrderInfo order,
                       List<FangguoAsset> assets, List<FangguoLabel> labels) {
        this.id = id;
        this.orderId = orderId;
        this.tid = tid;
        this.unitKey = unitKey;
        this.barcode = barcode;
        this.coverTaskId = coverTaskId;
        this.oid = oid;
        this.skuId = skuId;
        this.skuExtCode = skuExtCode;
        this.factoryEncode = factoryEncode;
        this.skuProps = skuProps;
        this.coverStatus = coverStatus;
        this.unitIdx = unitIdx;
        this.unitTotal = unitTotal;
        this.sourceCreatedAt = sourceCreatedAt;
        this.order = order;
        this.assets = assets;
        this.labels = labels;
    }

    public long id()                  { return id; }
    public long orderId()             { return orderId; }
    public String tid()               { return tid; }
    public String unitKey()           { return unitKey; }
    public String barcode()           { return barcode; }
    public String coverTaskId()       { return coverTaskId; }
    public String oid()               { return oid; }
    public String skuId()             { return skuId; }
    public String skuExtCode()        { return skuExtCode; }
    public String factoryEncode()     { return factoryEncode; }
    public String skuProps()          { return skuProps; }
    public String coverStatus()       { return coverStatus; }
    public Integer unitIdx()          { return unitIdx; }
    public Integer unitTotal()        { return unitTotal; }
    public String sourceCreatedAt()   { return sourceCreatedAt; }
    public FangguoOrderInfo order()   { return order; }
    public List<FangguoAsset> assets() { return assets; }
    public List<FangguoLabel> labels() { return labels; }

    public static FangguoItem fromJson(Map<String, Object> o) {
        List<FangguoAsset> assets = new ArrayList<>();
        for (Object raw : Json.list(o, "assets")) {
            assets.add(FangguoAsset.fromJson(Json.asMap(raw)));
        }
        List<FangguoLabel> labels = new ArrayList<>();
        for (Object raw : Json.list(o, "labels")) {
            labels.add(FangguoLabel.fromJson(Json.asMap(raw)));
        }
        return new FangguoItem(
                Json.lng(o, "id"),
                Json.lng(o, "order_id"),
                Json.str(o, "tid"),
                Json.str(o, "unit_key"),
                Json.str(o, "barcode"),
                Json.str(o, "cover_task_id"),
                Json.str(o, "oid"),
                Json.str(o, "sku_id"),
                Json.str(o, "sku_ext_code"),
                Json.str(o, "factory_encode"),
                Json.str(o, "sku_props"),
                Json.str(o, "cover_status"),
                nullableInt(o.get("unit_idx")),
                nullableInt(o.get("unit_total")),
                Json.str(o, "source_created_at"),
                FangguoOrderInfo.fromJson(Json.obj(o, "order")),
                Collections.unmodifiableList(assets),
                Collections.unmodifiableList(labels));
    }

    private static Integer nullableInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
