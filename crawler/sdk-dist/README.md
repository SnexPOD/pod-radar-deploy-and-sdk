# pod-radar 爬虫系统 Java SDK

给第三方只需要这个 jar（已自包含 `sdk-core`）：

```text
crawler-sdk-0.1.0.jar
```

## 环境

需要 JDK 8+。

```bash
export CRAWLER_ENDPOINT="http://10.10.131.205"   # 爬虫后端地址（crawler-api，默认 :3002 / 反代后的对外地址）
export CRAWLER_API_KEY="你的爬虫 API Key"          # 爬虫系统独立鉴权（x-api-key）
```

## 启动示例

在当前目录直接编译并运行 Demo：

```bash
javac -cp crawler-sdk-0.1.0.jar CrawlerDemo.java

# 列最近的同步 run
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo list 10

# 跨 run 查失败 item（顺带打印「历史订单门」天数 historyOrderDays）
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo items failed 20

# 跨 run 查指定订单创建时间范围内的失败 item（epoch milliseconds，闭区间）
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo items failed 20 1781136000000 1781222399999

# 触发一次增量同步
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo start

# 按类型批量重试失败素材（只重试 historyOrderDays 天内的订单）
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo retry product_image
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo retry label 372

# 强制游标到某时间 / 清空
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo cursor 2025-09-01T00:00:00+08:00
java -cp .:crawler-sdk-0.1.0.jar CrawlerDemo cursor null
```

> Windows 下 classpath 分隔符用 `;`：`-cp .;crawler-sdk-0.1.0.jar`。

`CrawlerDemo.class` 是 `javac` 编译后的临时产物，不需要交付给第三方。

## 业务项目引用（Maven）

```xml
<dependency>
    <groupId>io.podradar</groupId>
    <artifactId>crawler-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

Maven 会自动带上传递依赖 `sdk-core`，业务工程无需手动加 classpath。完整 API 见仓库根 `sdk/` 源码与 `CrawlerClient`。

## Item 筛选

`GET /api/v1/hihumbird/items` 的时间范围参数都是 epoch milliseconds，闭区间：

- `created_from` / `created_to`：订单创建时间，对应 hihumbird `source_data.created`。
- `production_from` / `production_to`：订单开始生产时间，对应 `source_data.begin_production_time`。

SDK 写法：

```java
ItemsFilter filter = ItemsFilter.empty()
        .withCreatedRange(1781136000000L, 1781222399999L)
        .withProductionFrom(1781222400000L)
        .withPage(PageQuery.of(20, 0));

ItemsListResponse items = crawler.listItems(filter);
```

批量重试失败素材复用同一组筛选字段：

```java
RetryFailedKindRequest req = RetryFailedKindRequest
        .of(RetryFailedKind.PRODUCT_IMAGE)
        .withFilter(filter); // 自动带 run/q/订单号/时间范围，忽略 crawl_status 和分页

RetryFailedKindResponse retry = crawler.retryFailedKind(req);
```

## 关于「历史订单门」

订单项创建时间超过服务端 `CRAWLER_HISTORY_ORDER_DAYS`（默认 90）天的老订单：自动同步只爬生产图+源图、跳过商品图与面单；四个「重试失败」批量动作也只重试该天数内的订单。`CrawlerDemo items` 打印的 `historyOrderDays` 即该窗口；单条 `retryItem` 不受此限制。

前端“90天内生产项”按自然日传 `created_from`/`created_to`：第 N 天前 `00:00:00.000` 到今天 `23:59:59.999`。“历史生产项”覆盖所有历史页数据，默认不传 `created_to`；只有用户手动选择订单创建日期时才传创建时间范围。
