package io.podradar.crawler.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Body for {@code POST /api/v1/hihumbird/retry-failed} — retry all failed assets of one
 * {@link RetryFailedKind} within the current item filter. Filters mirror {@link ItemsFilter}
 * (but {@code crawl_status} is intentionally not sent: the {@code kind} itself is the failure
 * dimension). The scope follows the filter: with {@code run_id} it retries that batch's items,
 * otherwise the whole crawl DB.
 */
public final class RetryFailedKindRequest {
    private final RetryFailedKind kind;
    private Long runId;
    private String q;
    private String salesOrderNo;
    private String productionBatchCode;
    private String productionOrderItemCode;
    private String trackNumber;
    private String statusName;
    private Long createdFrom;
    private Long createdTo;
    private Long productionFrom;
    private Long productionTo;

    private RetryFailedKindRequest(RetryFailedKind kind) {
        this.kind = kind;
    }

    public static RetryFailedKindRequest of(RetryFailedKind kind) {
        if (kind == null) throw new IllegalArgumentException("kind is required");
        return new RetryFailedKindRequest(kind);
    }

    public RetryFailedKindRequest withRunId(long id)                       { this.runId = id; return this; }
    public RetryFailedKindRequest withQuery(String text)                   { this.q = text; return this; }
    public RetryFailedKindRequest withSalesOrderNo(String no)              { this.salesOrderNo = no; return this; }
    public RetryFailedKindRequest withProductionBatchCode(String code)     { this.productionBatchCode = code; return this; }
    public RetryFailedKindRequest withProductionOrderItemCode(String code) { this.productionOrderItemCode = code; return this; }
    public RetryFailedKindRequest withTrackNumber(String num)              { this.trackNumber = num; return this; }
    public RetryFailedKindRequest withStatusName(String name)              { this.statusName = name; return this; }
    public RetryFailedKindRequest withCreatedFrom(long epochMs)            { this.createdFrom = epochMs; return this; }
    public RetryFailedKindRequest withCreatedTo(long epochMs)              { this.createdTo = epochMs; return this; }
    public RetryFailedKindRequest withCreatedRange(long fromMs, long toMs) {
        this.createdFrom = fromMs;
        this.createdTo = toMs;
        return this;
    }
    public RetryFailedKindRequest withProductionFrom(long epochMs)         { this.productionFrom = epochMs; return this; }
    public RetryFailedKindRequest withProductionTo(long epochMs)           { this.productionTo = epochMs; return this; }
    public RetryFailedKindRequest withProductionRange(long fromMs, long toMs) {
        this.productionFrom = fromMs;
        this.productionTo = toMs;
        return this;
    }

    /** Adopt the filters from an {@link ItemsFilter} ({@code crawl_status} and page are ignored). */
    public RetryFailedKindRequest withFilter(ItemsFilter f) {
        if (f.runId() != null) this.runId = f.runId();
        this.q = f.query();
        this.salesOrderNo = f.salesOrderNo();
        this.productionBatchCode = f.productionBatchCode();
        this.productionOrderItemCode = f.productionOrderItemCode();
        this.trackNumber = f.trackNumber();
        this.statusName = f.statusName();
        this.createdFrom = f.createdFrom();
        this.createdTo = f.createdTo();
        this.productionFrom = f.productionFrom();
        this.productionTo = f.productionTo();
        return this;
    }

    public RetryFailedKind kind() { return kind; }

    public Map<String, Object> toJson() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("kind", kind.wire());
        if (runId != null) o.put("run_id", runId);
        if (q != null) o.put("q", q);
        if (salesOrderNo != null) o.put("sales_order_no", salesOrderNo);
        if (productionBatchCode != null) o.put("production_batch_code", productionBatchCode);
        if (productionOrderItemCode != null) o.put("production_order_item_code", productionOrderItemCode);
        if (trackNumber != null) o.put("track_number", trackNumber);
        if (statusName != null) o.put("status_name", statusName);
        if (createdFrom != null) o.put("created_from", createdFrom);
        if (createdTo != null) o.put("created_to", createdTo);
        if (productionFrom != null) o.put("production_from", productionFrom);
        if (productionTo != null) o.put("production_to", productionTo);
        return o;
    }
}
