# pod-radar 部署 + SDK

本仓库是 [`pod-radar`](https://github.com/Open2Any/pod-radar) 主仓库的**部署 + SDK 子仓库**（以 git submodule 引入）。把「部署」和「Java SDK」打包成**两个独立场景包**，按需取用其一即可。

## 两个场景包

| 目录 | 系统 | 部署 | SDK |
| --- | --- | --- | --- |
| [`image-search/`](image-search/) | 图像向量检索（主系统） | `deploy/`（compose + nginx） | `sdk-dist/`（`podradar-sdk` 单 jar） |
| [`crawler/`](crawler/) | hihumbird 爬虫系统 | `deploy/`（compose + nginx；browserless 已是 compose 内的服务） | `sdk-dist/`（`crawler-sdk` 单 jar，已自包含 `sdk-core`） |

每个场景包**自包含**：进对应目录，照 `deploy/` 部署、照 `sdk-dist/` 接 SDK 即可，两个系统互不依赖。

## 部署（默认从 Docker Hub 拉取预构建镜像）

无需自行构建镜像。compose 默认拉 `codedevin/pod-radar-main` / `codedevin/pod-radar-crawler`：

```bash
cd image-search/deploy        # 或 cd crawler/deploy
cp compose.env.example .env   # 爬虫是 compose.crawler.env.example；按需改 .env
docker compose -f compose.yml pull        # 爬虫用 -f compose.crawler.yml
docker compose -f compose.yml up -d
```

- 镜像版本/仓库可在 `.env` 用 `MAIN_IMAGE` / `CRAWLER_IMAGE` 覆盖。
- 各场景的环境变量表、反代配置见该目录 `deploy/README.*.md` 与 `deploy/nginx/`。
- 爬虫的 browserless（商品图无头浏览器渲染）已作为 `compose.crawler.yml` 内的服务，`up -d` 时一并起，无需单独启动。

## SDK

- **现成 jar + Demo**：见 `<场景>/sdk-dist/`（含 `README.md` 编译运行说明）。两个 SDK 各是一个自包含 jar，直接 `javac -cp <jar>` 即可。
- **源码**：[`sdk/`](sdk/) 是两个 SDK 的完整源码（`sdk-core` / `podradar-sdk` / `crawler-sdk` / `examples`），可自行 `cd sdk && mvn -q clean install` 构建（产物 Java 8；构建机 JDK 11+ / Maven 3.9+）。

## 来源

本仓库内容由主仓库 `pod-radar` 打包而来（部署来自 `docker/` + `deploy/`，SDK 来自 `java_sdk/`）；以主仓库为准，本仓库随版本同步更新。
