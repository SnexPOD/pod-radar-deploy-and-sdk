package io.podradar.crawler.model;

import io.podradar.sdk.model.PageQuery;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cross-run item filter for {@code GET /api/v1/hihumbird/items}. */
public final class ItemsFilter {
    private Long runId;
    private String q;
    private String salesOrderNo;
    private String productionBatchCode;
    private String productionOrderItemCode;
    private String trackNumber;
    private String statusName;
    private CrawlStatus crawlStatus;
    private Long createdFrom;
    private Long createdTo;
    private Long productionFrom;
    private Long productionTo;
    private PageQuery page = PageQuery.of(10, 0);

    private ItemsFilter() {}

    public static ItemsFilter empty() { return new ItemsFilter(); }

    public ItemsFilter withRunId(long id)                        { this.runId = id; return this; }
    public ItemsFilter withQuery(String text)                    { this.q = text; return this; }
    public ItemsFilter withSalesOrderNo(String no)               { this.salesOrderNo = no; return this; }
    public ItemsFilter withProductionBatchCode(String code)      { this.productionBatchCode = code; return this; }
    public ItemsFilter withProductionOrderItemCode(String code)  { this.productionOrderItemCode = code; return this; }
    public ItemsFilter withTrackNumber(String num)               { this.trackNumber = num; return this; }
    public ItemsFilter withStatusName(String name)               { this.statusName = name; return this; }
    public ItemsFilter withCrawlStatus(CrawlStatus s)            { this.crawlStatus = s; return this; }
    public ItemsFilter withCreatedFrom(long epochMs)             { this.createdFrom = epochMs; return this; }
    public ItemsFilter withCreatedTo(long epochMs)               { this.createdTo = epochMs; return this; }
    public ItemsFilter withCreatedRange(long fromMs, long toMs)  { this.createdFrom = fromMs; this.createdTo = toMs; return this; }
    public ItemsFilter withProductionFrom(long epochMs)          { this.productionFrom = epochMs; return this; }
    public ItemsFilter withProductionTo(long epochMs)            { this.productionTo = epochMs; return this; }
    public ItemsFilter withProductionRange(long fromMs, long toMs) {
        this.productionFrom = fromMs;
        this.productionTo = toMs;
        return this;
    }
    public ItemsFilter withPage(PageQuery page)                  { this.page = page; return this; }

    public Long runId()                       { return runId; }
    public String query()                     { return q; }
    public String salesOrderNo()              { return salesOrderNo; }
    public String productionBatchCode()       { return productionBatchCode; }
    public String productionOrderItemCode()   { return productionOrderItemCode; }
    public String trackNumber()               { return trackNumber; }
    public String statusName()                { return statusName; }
    public CrawlStatus crawlStatus()          { return crawlStatus; }
    public Long createdFrom()                 { return createdFrom; }
    public Long createdTo()                   { return createdTo; }
    public Long productionFrom()              { return productionFrom; }
    public Long productionTo()                { return productionTo; }
    public PageQuery page()                   { return page; }

    /** Query parameters as a key-value map (in insertion order). Values are pre-encoded strings. */
    public Map<String, String> toQueryParams() {
        Map<String, String> q = new LinkedHashMap<>();
        if (runId != null) q.put("run_id", String.valueOf(runId));
        if (this.q != null) q.put("q", this.q);
        if (salesOrderNo != null) q.put("sales_order_no", salesOrderNo);
        if (productionBatchCode != null) q.put("production_batch_code", productionBatchCode);
        if (productionOrderItemCode != null) q.put("production_order_item_code", productionOrderItemCode);
        if (trackNumber != null) q.put("track_number", trackNumber);
        if (statusName != null) q.put("status_name", statusName);
        if (crawlStatus != null) q.put("crawl_status", crawlStatus.wire());
        if (createdFrom != null) q.put("created_from", String.valueOf(createdFrom));
        if (createdTo != null) q.put("created_to", String.valueOf(createdTo));
        if (productionFrom != null) q.put("production_from", String.valueOf(productionFrom));
        if (productionTo != null) q.put("production_to", String.valueOf(productionTo));
        q.put("limit", String.valueOf(page.limit()));
        q.put("offset", String.valueOf(page.offset()));
        return q;
    }
}
