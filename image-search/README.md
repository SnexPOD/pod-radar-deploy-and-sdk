# 图像向量检索（主系统）· 部署 + SDK

独立交付包：部署主系统 + 接入图像搜索 Java SDK。与爬虫系统无关。

## 部署（Docker Hub 拉取，无需构建）

```bash
cd deploy
cp compose.env.example .env        # 按需改：MAIN_IMAGE、数据库、S3/OSS、Embedding provider 等
docker compose -f compose.yml pull
docker compose -f compose.yml up -d
```

- 镜像默认 `codedevin/pod-radar-main:v1.0.0`（`.env` 里 `MAIN_IMAGE` 可覆盖）。
- 完整环境变量表、外部 S3/Embedding 说明：见 [`deploy/README.main.md`](deploy/README.main.md)。
- 反向代理参考：[`deploy/nginx/`](deploy/nginx/)。

## SDK

- 现成 jar + Demo：见 [`sdk-dist/`](sdk-dist/)（`podradar-sdk-0.1.0.jar` 单 jar 自包含 + `ImageSearchDemo.java` + 说明）。
- 源码 / 自行构建：见仓库根 [`../sdk/`](../sdk/)。
