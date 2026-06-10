# pod-radar Java SDK

Java 8 clients for the pod-radar HTTP API. **Zero runtime dependencies** —
only `java.net.HttpURLConnection` from the JDK. Jar size ≤ 80KB (main) / 60KB
(crawler).

> **使用文档(权威入口)**: https://podradar.example.com/sdk
>
> 第三方接入只需要两样东西 —— 一个 jar 包(见下面 §1)和上面这个文档 URL。

---

## 1. 安装

### pod图像向量化系统 SDK · Maven Central

\`\`\`xml
<dependency>
    <groupId>io.podradar</groupId>
    <artifactId>podradar-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
\`\`\`

### 爬虫 SDK · 公司内 Nexus(不上 Central)

\`\`\`xml
<dependency>
    <groupId>io.podradar</groupId>
    <artifactId>crawler-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
\`\`\`

Nexus 配置 + Gradle 写法 + 验证 `mvn dependency:tree` 见 https://podradar.example.com/sdk §2。

---

## 2. 从源码构建(本地开发 / pre-release 测试)

SDK 产物是 Java 8（javac release=8）。构建机需要 JDK 11+ 和 Maven 3.9+（测试代码
依赖 wiremock 3.x，按 testRelease 11 编译；只随构建跑，不随 jar 交付）:

\`\`\`bash
cd sdk
mvn -q clean install -DskipTests=false     # 编译 + 跑全部单测 + 装到本地 ~/.m2
\`\`\`

之后业务工程的 `pom.xml` 就能直接引入 `0.1.0`:

\`\`\`xml
<dependency>
    <groupId>io.podradar</groupId>
    <artifactId>podradar-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
\`\`\`

### 单模块开发循环

\`\`\`bash
mvn -pl sdk-core,podradar-sdk -am test       # 主 SDK + 共享底座
mvn -pl crawler-sdk -am test                 # 爬虫 SDK
mvn -pl examples -am compile                 # 样例只编译,跑要 endpoint + key
\`\`\`

---

## 3. 模块结构

| 模块 | groupId : artifactId | 说明 |
|---|---|---|
| `sdk-core/` | `io.podradar:sdk-core` | 共享底座: `HttpExecutor` / `Json` / `Multipart` / 异常树 |
| `podradar-sdk/` | `io.podradar:podradar-sdk` | pod图像向量化系统 SDK · 同步 `PodRadarClient` + 异步 `PodRadarAsyncClient` |
| `crawler-sdk/` | `io.podradar:crawler-sdk` | 爬虫 SDK · 同步 `CrawlerClient` + 异步 `CrawlerAsyncClient` |
| `examples/` | (不发布) | 5 个可编译的样例程序,演示典型用法 |

主、爬虫两个 jar 包名隔离(`io.podradar.sdk.*` / `io.podradar.crawler.*`),业务方可单引或同引,不会撞符号。

---

## 4. 三行代码:搜图

\`\`\`java
try (PodRadarClient client = PodRadarClient.builder()
        .endpoint("https://api.podradar.example.com")
        .apiKey(System.getenv("POD_RADAR_API_KEY"))
        .build()) {
    SearchResponse r = client.search(SearchRequest.fromFile(new File("q.jpg"), 24));
    r.results().forEach(h ->
        System.out.printf("%d  %.4f  %s%n", h.imageId(), h.score(), h.full()));
}
\`\`\`

更多模式(URL / bytes / 纯文本)、上传、批量、爬虫触发 run、异步 client、异常分类 ——
**全部在使用文档 https://podradar.example.com/sdk 里**,本 README 不重复。

---

## 5. 版本兼容

| SDK | HTTP 表面 | 兼容承诺 |
|---|---|---|
| 主 SDK `1.x` | `/api/v1/*` | 至少 12 个月不破坏 |
| 爬虫 SDK `1.x` | 爬虫后端 `/api/v1/*` | 至少 6 个月不破坏(内部消费者) |

`0.x` 预览版可能出现 break 改动。

---

## 6. 反馈 / 报 bug

- **业务方接入问题**:首先翻 https://podradar.example.com/sdk §10 常见问题
- **疑似 SDK bug**:开 GitHub issue,**贴 `X-Request-Id` 响应头**(异常对象上有 `requestId()` getter)
- **服务端契约疑问**:见 https://podradar.example.com/sdk 链接到的 \`HTTP_CONTRACT.md\` / \`CRAWLER_HTTP_CONTRACT.md\`(内部仓库)

---

## 7. 内部贡献者(repo 视角)

| 路径 | 说明 |
|---|---|
| [`/docs/sdk/`](../docs/sdk/) | 设计稿:`DESIGN.md` / `JAVA_SDK.md` / `CRAWLER_SDK.md` / HTTP 契约 |
| [`/docs/sdk/USAGE.md`](../docs/sdk/USAGE.md) | 使用文档的 markdown 源(与 `packages/web/app/sdk/usage.ts` 保持一致) |
| [`packages/web/app/sdk/`](../packages/web/app/sdk/) | `/sdk` Next.js 路由,渲染对外文档 |
| [`packages/backend/src/server.ts`](../packages/backend/src/server.ts) | `/api/v1/*` alias 挂载点 |
| 主仓 [`CLAUDE.md`](../CLAUDE.md) | 仓库导航 + 工作约束 |
