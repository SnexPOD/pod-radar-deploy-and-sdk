package io.podradar.crawler.model;

/**
 * Per-item crawl outcome filter for {@link FangguoItemsFilter#withCrawlStatus}. fangguo only
 * distinguishes three buckets (no per-kind failure filters like hihumbird):
 * <ul>
 *   <li>{@code OK} — no failed asset or label under the unit/order</li>
 *   <li>{@code FAILED} — at least one failed asset or label</li>
 *   <li>{@code PARTIAL} — both a failed and a fetched/converted artifact exist</li>
 * </ul>
 */
public enum FangguoCrawlStatus {
    OK("ok"),
    FAILED("failed"),
    PARTIAL("partial");

    private final String wire;

    FangguoCrawlStatus(String wire) { this.wire = wire; }

    public String wire() { return wire; }
}
