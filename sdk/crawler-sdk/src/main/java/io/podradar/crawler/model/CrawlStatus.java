package io.podradar.crawler.model;

/** Per-item crawl outcome filter used by {@link ItemsFilter#withCrawlStatus}. */
public enum CrawlStatus {
    OK("ok"),
    FAILED("failed"),
    PARTIAL("partial"),
    PRODUCT_IMAGE_FAILED("product_image_failed"),
    SHIPPING_LABEL_FAILED("shipping_label_failed"),
    SOURCE_IMAGE_FAILED("source_image_failed"),
    PRODUCTION_IMAGE_FAILED("production_image_failed");

    private final String wire;

    CrawlStatus(String wire) { this.wire = wire; }

    public String wire() { return wire; }
}
