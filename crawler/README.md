# 爬虫系统 · 部署 + SDK

部署爬虫系统 + 接入爬虫 Java SDK。与图搜主系统无关（独立库 / 独立桶 / 独立鉴权）。

爬虫服务带**两个数据源**：**hihumbird**（路由在 `/api/v1/hihumbird/*`，SDK 直接挂在 `CrawlerClient` 上）和 **fangguo 方果ERP**（路由在 `/api/v1/fangguo/*`，SDK 经 `crawler.fangguo()` 子访问器取得）。两源共用同一端点 / API key，类型互相隔离。

## 部署（Docker Hub 拉取，无需构建）

browserless（商品图无头浏览器渲染）已是 `compose.crawler.yml` 内的服务，`up -d` 时一并起，无需单独启动：

```bash
cd deploy
cp compose.crawler.env.example .env     # 按需改：CRAWLER_IMAGE、库、S3、hihumbird 账号、CRAWLER_HISTORY_ORDER_DAYS 等
docker compose -f compose.crawler.yml pull
docker compose -f compose.crawler.yml up -d
```

- 镜像默认 `codedevin/pod-radar-crawler:v1.0.0`（`.env` 里 `CRAWLER_IMAGE` 可覆盖）。
- 完整环境变量表（含 `HIHUMBIRD_FETCHER_REPLICAS`、`CRAWLER_HISTORY_ORDER_DAYS` 等）：见 [`deploy/README.crawler.md`](deploy/README.crawler.md)。
- 反向代理参考：[`deploy/nginx/`](deploy/nginx/)。

## SDK

- 现成 jar + Demo：见 [`sdk-dist/`](sdk-dist/)。爬虫 SDK 是**单个自包含 jar**（`crawler-sdk-0.1.0.jar`，已内含 `sdk-core`），`javac -cp crawler-sdk-0.1.0.jar` 即可，详见该目录 `README.md`。
- 源码 / 自行构建：见仓库根 [`../sdk/`](../sdk/)。

## Item 筛选接口

`GET /api/v1/hihumbird/items` 支持的筛选参数：

| 参数 | 说明 |
| --- | --- |
| `run_id` | 限定某个同步 / 重试批次。 |
| `q` | 模糊搜索商品、SKU、订单等文本。 |
| `sales_order_no` | 销售订单号。 |
| `production_batch_code` | 生产批次号。 |
| `production_order_item_code` | 生产项编码。 |
| `track_number` | 物流单号 / 物流方式。 |
| `status_name` | hihumbird 订单状态名称。 |
| `crawl_status` | 爬取状态：`ok`、`failed`、`partial`、`product_image_failed`、`shipping_label_failed`、`source_image_failed`、`production_image_failed`。 |
| `created_from` / `created_to` | 订单创建时间范围，对应 hihumbird 原始 `source_data.created`。 |
| `production_from` / `production_to` | 订单开始生产时间范围，对应 `source_data.begin_production_time`。 |
| `limit` / `offset` | 分页参数。 |

时间字段均为 epoch milliseconds，闭区间。SDK 使用 `ItemsFilter.withCreatedRange(fromMs, toMs)` / `withProductionRange(fromMs, toMs)`；也可以分别调用 `withCreatedFrom`、`withCreatedTo`、`withProductionFrom`、`withProductionTo`。

`POST /api/v1/hihumbird/retry-failed` 的批量重试请求复用同一组筛选字段。SDK 可用 `RetryFailedKindRequest.of(kind).withFilter(filter)` 直接沿用当前 `ItemsFilter`（会忽略 `crawl_status` 和分页）。

### 方果(fangguo) Item 筛选

方果订单单元的字段与 hihumbird 不同（按订单单元 order_unit 维度），`GET /api/v1/fangguo/items` 的筛选项也更窄，只支持 4 个：

| 参数 | 说明 |
| --- | --- |
| `run_id` | 限定某个同步 / 重试批次（不存在时 404）。 |
| `q` | 模糊搜索 `tid` / `barcode` / `sku_ext_code` / `factory_encode` / `oid` / `store_name` / `shop_name`。 |
| `ship_status` | 订单发货状态（方果原始字段，自由字符串）。 |
| `crawl_status` | 爬取状态：`ok` / `failed` / `partial`。 |
| `limit` / `offset` | 分页参数。 |

返回的 item 字段（`FangguoItem`）：`tid`、`unit_key`、`barcode`、`sku_id` / `sku_ext_code` / `factory_encode`、`cover_task_id` / `cover_status`、`unit_idx` / `unit_total`，外加 `order`（`ship_status` / `store_name` / `shop_name` / `platform_desc` / `trade_id` / `has_package`）、`assets[]`（效果图 / 生产图 / 成品图）、`labels[]`（面单）。SDK 用 `FangguoItemsFilter.empty().withRunId(...) / withQuery(...) / withShipStatus(...) / withCrawlStatus(...) / withPage(...)`。

与 hihumbird 的差异：fangguo **没有** `rescan-pending-labels`、没有按 kind 批量重试、没有 `batch_code`；停止用 `POST /runs/:id/stop`（可带 `pause_auto`）而不是 `stop-retry`。完整 SDK 方法表见仓库根 [`docs/sdk/CRAWLER_SDK.md`](../../docs/sdk/CRAWLER_SDK.md) §4.6。

## 历史订单门（>90 天）

订单项创建超过 `CRAWLER_HISTORY_ORDER_DAYS`（默认 90）天的老订单：自动同步只爬生产图+源图、跳过商品图（无头浏览器）与面单；批量「重试失败」也只重试该天数内的订单。详见 `deploy/README.crawler.md`。

前端“90天内生产项”按自然日传 `created_from`/`created_to`：第 N 天前 `00:00:00.000` 到今天 `23:59:59.999`。“历史生产项”覆盖所有历史页数据，默认不传 `created_to`；只有用户手动选择订单创建日期时才传创建时间范围。
