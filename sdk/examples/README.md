# pod-radar SDK 样例程序

这些样例覆盖 `podradar-sdk` 和 `crawler-sdk` 当前暴露的主要调用面。每个程序都可以直接作为接入模板复制，输出会打印关键响应字段，方便联调时核对服务端返回。

> 当前版本是 `0.1.0`。从仓库根目录运行 Maven 时使用 `mvn -f java_sdk/pom.xml -pl examples -am ...`；从 `java_sdk/` 目录运行时使用 `mvn -pl examples -am ...`。

## 环境变量

```bash
export POD_RADAR_ENDPOINT="https://api.podradar.example.com"
export POD_RADAR_API_KEY="pod-radar_xxxxxxxxxxxxxxxx"

export POD_RADAR_CRAWLER_ENDPOINT="https://crawler.podradar.example.com"
export POD_RADAR_CRAWLER_KEY="pod-radar_xxxxxxxxxxxx"
```

可选:

```bash
export POD_RADAR_USER_AGENT="my-service/1.2.3"
export POD_RADAR_CRAWLER_USER_AGENT="ops-console/1.2.3"
export POD_RADAR_UPLOAD_URLS="https://cdn.example.com/a.jpg,https://cdn.example.com/b.jpg"
export POD_RADAR_SEARCH_URLS="https://cdn.example.com/q1.jpg https://cdn.example.com/q2.jpg"
```

## pod图像向量化系统

| 程序 | 覆盖的 SDK 调用 |
|---|---|
| `SearchExample.java` | `search(SearchRequest)` 四种输入: file / bytes / url / text,以及 `k`、`minScore`、image+text |
| `UploadExample.java` | `upload(UploadRequest)` 三种输入: file / bytes / url,以及 source/sourceId/title/tags/meta |
| `BatchUploadExample.java` | `uploadBatch`、`listJobItems`、`retryFailedJob` |
| `BatchSearchExample.java` | `searchBatch`、`getSearchJob`、`listSearchJobItems` |
| `ImageManagementExample.java` | `listImages`、`getImage` |
| `AsyncSearchExample.java` | `PodRadarAsyncClient` 的 search/upload/list/get,以及 `wrap(sync)` |

常用跑法:

```bash
mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.SearchExample \
  -Dexec.args="file /path/to/query.jpg --k 24 --min-score 0.85 --text 红色卫衣"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.SearchExample \
  -Dexec.args="text 复古黑色棒球帽 --k 12"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.UploadExample \
  -Dexec.args="url https://cdn.example.com/a.jpg --source snexdiy --source-id SKU-1 --title 红色帽子"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.BatchUploadExample \
  -Dexec.args="--source snexdiy --source-id batch-20260607"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.BatchSearchExample \
  -Dexec.args="--k 12 --min-score 0.82"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.ImageManagementExample \
  -Dexec.args="list --limit 20 --offset 0"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.AsyncSearchExample \
  -Dexec.args="search q1.jpg q2.jpg q3.jpg"
```

## 爬虫系统

| 程序 | 覆盖的 SDK 调用 |
|---|---|
| `CrawlerRunExample.java` | `startRun` incremental/backfill、`listRuns`、`listRunItems`、`retryFailedRun`、`rescanPendingLabels`、`rescanMissingBatches` |
| `CrawlerItemsExample.java` | `listItems(ItemsFilter)` 全部过滤项、`retryItem` |
| `CrawlerSettingsExample.java` | `getSettings`、`updateSettings`、settings/state 输出 |
| `CrawlerKeyExample.java` | `me`、`listKeys`、`createKey`、`revokeKey`、`deleteKey` |
| `CrawlerAsyncExample.java` | `CrawlerAsyncClient` 常用读接口和 `wrap(sync)` |

常用跑法:

```bash
mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
  -Dexec.args="start-incremental --wait"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
  -Dexec.args="start-backfill 1780780800000 1780867200000 --batch B-20260607 --dry-run"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerRunExample \
  -Dexec.args="rescan-missing-batches --account-id 9"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerItemsExample \
  -Dexec.args="list --status failed --sales-order SO-123 --limit 20"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerSettingsExample \
  -Dexec.args="get"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerKeyExample \
  -Dexec.args="create ops-bob"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.CrawlerAsyncExample \
  -Dexec.args="dashboard"
```

## 爬虫系统 — 方果(fangguo)

方果(方果ERP)是爬虫服务的第二个数据源，端点在 `/api/v1/fangguo/*`，复用同一个 `POD_RADAR_CRAWLER_ENDPOINT` / `POD_RADAR_CRAWLER_KEY`。所有调用走 `crawler.fangguo()` 子访问器，与 hihumbird 方法互不影响。

| 程序 | 覆盖的 SDK 调用 |
|---|---|
| `FangguoRunExample.java` | `fangguo().getSettings`、`startRun` incremental/backfill(+`--dry-run`/`--wait`)、`listRuns`、`getRun`、`listRunItems`、`listItems(FangguoItemsFilter)`、`stopRun`、`retryFailedRun`、`retryFailedAll`、`retryItem` |

与 hihumbird 的差异:无 `rescan-pending-labels`、无按 kind 批量重试、无 `batch_code`;停止用 `stop`(可带 `--pause-auto`)而不是 `stop-retry`;跨 run 查 item 只支持 `run_id` / `q` / `ship_status` / `crawl_status`。

常用跑法:

```bash
mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="settings"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="start-incremental --wait"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="start-backfill 1780780800000 1780867200000 --dry-run"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="list --limit 10"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="items 1234 --limit 20"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="cross-items --q ABC123 --ship-status shipped --crawl-status failed"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="stop 1234 --pause-auto"

mvn -pl examples -am exec:java \
  -Dexec.mainClass=io.podradar.examples.FangguoRunExample \
  -Dexec.args="retry-item 56789"
```

## 注意

- API key 只从环境变量读取，不要写进源码、日志或脚本参数。
- `crawler.createKey` 返回的 `plaintext` 只出现一次，服务端不会再返回。
- `deleteKey` 示例要求 `--confirm-delete`；正常运维删除 key 应优先用 `revoke`。
- 异步 client 的业务异常在 `ExecutionException.getCause()` 或 `CompletionException.getCause()` 里，需要解包后按 `PodRadar*Exception` 处理。
