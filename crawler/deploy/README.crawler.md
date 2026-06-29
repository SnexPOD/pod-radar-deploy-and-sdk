# Crawler Docker Deployment

This guide covers `compose.crawler.yml`, the standalone hihumbird crawler system. Treat this directory as a standalone deployment repository.

The Docker stack does not start MinIO. Configure an external S3-compatible object store with `CRAWLER_S3_*` variables and create the buckets before starting workers.

Browserless is still part of this stack because hihumbird product images require a headless browser to trigger the merchant page's frontend rendering.

## Services

| Service | Purpose |
| --- | --- |
| `crawler-postgres` | Standalone crawler database. |
| `crawler-db-push` | Applies crawler database schema at startup. |
| `pod-radar-crawler-api` | Crawler API. |
| `pod-radar-crawler-web` | Crawler web UI. |
| `pod-radar-hihumbird-sync` | hihumbird order synchronization scheduler. |
| `pod-radar-hihumbird-fetcher` | Downloads product images, production images, source images, PDFs, and label images. Normal memory cap; `--max-attempts 2`. |
| `pod-radar-hihumbird-fetcher-highmem` | Single high-memory hihumbird fetcher (`--max-attempts 5`) that picks up oversized images the normal fetcher OOM-crashed on, and helps with normal images otherwise. |
| `pod-radar-fangguo-sync` | Fangguo (方果ERP) order synchronization scheduler. |
| `pod-radar-fangguo-fetcher` | Downloads fangguo assets and label images (public direct links, no headless harvest). |
| `browserless` | Headless Chromium service used by the product-image harvest worker. |
| `pod-radar-hihumbird-harvest` | Opens the hihumbird factory page and triggers frontend product-image rendering. |

## Deploy

1. Copy the example env in this deployment directory:

   ```bash
   cp compose.crawler.env.example .env
   ```

   If deploying the main stack on the same machine, merge `compose.env.example` into the same `.env`.

2. Edit `.env`:

   - Set `CRAWLER_IMAGE` to the image tag you want to deploy.
   - Set a strong `CRAWLER_POSTGRES_PASSWORD`.
   - Fill `CRAWLER_S3_ENDPOINT`, `CRAWLER_S3_ACCESS_KEY_ID`, `CRAWLER_S3_SECRET_ACCESS_KEY`, and bucket names.
   - Create the crawler S3 buckets before starting workers.
   - Set `CRAWLER_ADMIN_KEY`.
   - Set `IMAGE_TOKEN_SECRET` to a long random value, for example `openssl rand -hex 32`.
   - Set `HIHUMBIRD_USERNAME` and `HIHUMBIRD_PASSWORD`.
   - Keep `CRAWLER_HARVEST_ENABLED=true` for product-image rendering, or set it to `false` to disable the headless harvest worker.

3. Start:

   ```bash
   docker compose -f compose.crawler.yml pull
   docker compose -f compose.crawler.yml up -d
   ```

   To mirror the PM2 setup of `6 instances x 8 concurrency` (= 48 parallel downloads), set `HIHUMBIRD_FETCHER_REPLICAS=6` and `HIHUMBIRD_FETCHER_CONCURRENCY=8` in `.env`. Do not run PM2 inside the worker container.

4. Check status and logs:

   ```bash
   docker compose -f compose.crawler.yml ps
   docker compose -f compose.crawler.yml logs -f pod-radar-hihumbird-sync
   docker compose -f compose.crawler.yml logs -f pod-radar-hihumbird-harvest
   ```

5. Open the crawler UI:

   ```text
   http://<server-host>:5175/crawler
   ```

## Environment Variables

### Host Ports And Browserless

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_IMAGE` | No | `codedevin/pod-radar-crawler:v1.0.7` | Application image used by crawler API, web, sync, fetcher, and harvest workers. |
| `CRAWLER_POSTGRES_HOST_BIND` | No | `0.0.0.0` | Host interface for the crawler database. |
| `CRAWLER_POSTGRES_HOST_PORT` | No | `5545` | Host port mapped to the crawler database. |
| `CRAWLER_API_HOST_BIND` | No | `0.0.0.0` | Host interface for the crawler API. |
| `CRAWLER_API_PORT` | No | `3002` | Host port for `pod-radar-crawler-api`. |
| `CRAWLER_WEB_HOST_BIND` | No | `0.0.0.0` | Host interface for the crawler web UI. |
| `CRAWLER_WEB_PORT` | No | `5175` | Host port for `pod-radar-crawler-web`. |
| `BROWSERLESS_HOST_BIND` | No | `0.0.0.0` | Host interface for Browserless. |
| `BROWSERLESS_HOST_PORT` | No | `3010` | Host port for Browserless. |
| `BROWSERLESS_CONCURRENT` | No | `3` | Browserless concurrent browser/session limit. |
| `BROWSERLESS_TIMEOUT` | No | `600000` | Browserless timeout in milliseconds. |

### Database

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_POSTGRES_USER` | Yes | `pod_radar` | Database user created in `crawler-postgres`. |
| `CRAWLER_POSTGRES_PASSWORD` | Yes | `pod_radar_pass` | Database password. Change this in production. |
| `CRAWLER_DATABASE_URL` | Yes | `postgres://pod_radar:pod_radar_pass@127.0.0.1:5545/pod_radar_crawler` | Host-side crawler database URL for local CLI commands. Inside containers, compose overrides it to `crawler-postgres:5432`. |

### Browser-Visible URLs

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_ASSET_PREFIX` | No | empty | Public prefix for crawler-web static assets. Leave empty for a standalone crawler domain. Set only when a CDN or proxy serves Next assets under a subpath. |
| `CRAWLER_WEB_URL` | No | empty | Optional browser-visible crawler web URL used by redirects/links. Leave empty for the standalone crawler web app. |

### Runtime API And Web

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_HOST` | No | `127.0.0.1` | Host-side crawler API bind address for local non-Docker runs. Containers use `0.0.0.0`. |
| `CRAWLER_PORT` | No | `3002` | Crawler API port. |
| `CRAWLER_WEB_HOST` | No | `127.0.0.1` | Host-side crawler web bind address for local non-Docker runs. Containers use `0.0.0.0`. |
| `CRAWLER_WEB_PORT` | No | `5175` | Crawler web port. |
| `CRAWLER_BACKEND_URL` | No | `http://127.0.0.1:3002` | Backend URL for local non-Docker runs. Containers use `http://pod-radar-crawler-api:3002`. |
| `CRAWLER_CORS_ORIGINS` | No | `http://127.0.0.1:5175` | Optional comma-separated CORS allowlist. |

### External Crawler S3

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_S3_PROVIDER` | Yes | `s3` | Storage provider mode. Use `s3` for external S3-compatible storage. |
| `CRAWLER_S3_URL_STYLE` | Yes | `virtual-hosted` | URL style: `virtual-hosted` for AWS-like S3, `path` for path-style endpoints. |
| `CRAWLER_S3_ENDPOINT` | Yes | `https://s3.example.com` | External object-storage endpoint; presigned/object URLs are built from this public endpoint. |
| `CRAWLER_S3_INTERNAL_ENDPOINT` | No (recommended on same-region cloud) | `https://oss-cn-hangzhou-internal.aliyuncs.com` | Internal/VPC endpoint for object read/write. On a same-region cloud host set this for free, unmetered, unthrottled transfer; without it large image PUTs go over the public endpoint and time out (socket idle timeout). Presigned URLs still use the public `CRAWLER_S3_ENDPOINT`. Empty off-cloud. |
| `CRAWLER_S3_REGION` | Yes | `us-east-1` | S3 region. |
| `CRAWLER_S3_ACCESS_KEY_ID` | Yes | `AKIA...` | S3 access key. |
| `CRAWLER_S3_SECRET_ACCESS_KEY` | Yes | `...` | S3 secret key. |
| `CRAWLER_S3_BUCKET_IMAGES` | Yes | `pod-radar-crawler-images` | Bucket for crawler images and label page images. |
| `CRAWLER_S3_BUCKET_DOCS` | Yes | `pod-radar-crawler-docs` | Bucket for original PDF documents. |
| `CRAWLER_BUCKET_ACCESS` | Yes | `public` | `public` returns direct object URLs; `private` returns presigned URLs. |

### Crawler Admin

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_ADMIN_NAME` | Yes | `admin` | Admin name used by the crawler API. |
| `CRAWLER_ADMIN_KEY` | Yes | `pod-radar_replace_with_a_long_random_value` | Admin API key. Must start with `pod-radar_` and be at least 20 characters. |
| `IMAGE_TOKEN_SECRET` | Yes | `<long random value>` | Shared production HMAC secret for image-token signing. Generate with `openssl rand -hex 32`. |
| `CRAWLER_CREDENTIAL_SECRET` | Yes if managing accounts | `<long random value>` | AES-256-GCM key encrypting upstream account credentials in `crawler_accounts`. Required once you manage/crawl accounts via the accounts API. Lose it and every stored account must be re-entered. |
| `CRAWLER_ADMIN_API_KEYS` | No | `pod-radar_key1,pod-radar_key2` | Optional legacy comma-separated fallback admin keys for multi-key deployments. |

Use `CRAWLER_ADMIN_KEY` as the bootstrap key. For additional operator keys, sign in to the crawler UI and create keys there.

### Docker Logs

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `DOCKER_LOG_MAX_SIZE` | No | `50m` | Max size of one Docker `json-file` log segment per container. |
| `DOCKER_LOG_MAX_FILE` | No | `10` | Number of rotated log segments per container. `50m × 10` caps each container at about 500M. |

### Hihumbird / Flyshark

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `HIHUMBIRD_USERNAME` | No | `147...` | hihumbird account username. Fallback; accounts are normally managed via the accounts API. |
| `HIHUMBIRD_PASSWORD` | No | `...` | hihumbird account password. Fallback; see above. |
| `HIHUMBIRD_BASE_URL` | No | `https://apigw.hihumbird.com` | Upstream API gateway (not the merchant portal). A per-account `base_url` overrides it. |
| `HIHUMBIRD_GROUP_ID` | Yes | `102420649` | hihumbird login group ID. |
| `HIHUMBIRD_APP_ID` | Yes | `2572668` | hihumbird app ID. |
| `HIHUMBIRD_REL_TYPE` | Yes | `2` | hihumbird login relation type. |
| `HIHUMBIRD_SCENE_ID` | Yes | `1f1o1jf9` | hihumbird login scene ID. |
| `HIHUMBIRD_CURSOR_START_AT` | No | `2025-05-06T00:00:00+08:00` | Initial incremental cursor start time. |

### Fangguo (方果ERP)

Second crawler source. Auth is a Bearer `accessToken` from login plus a fixed tenant-id header (no HMAC signing).

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `FANGGUO_USERNAME` | No | `...` | Fangguo account username. Fallback; accounts are normally managed via the accounts API. |
| `FANGGUO_PASSWORD` | No | `...` | Fangguo account password. Fallback; see above. |
| `FANGGUO_BASE_URL` | No | `https://fangguo.com/fgapp` | Fangguo API base URL. |
| `FANGGUO_TENANT_ID` | No | `3062076` | Optional pin for the tenant-id header. The INTERNAL tenant id, NOT the UI 系统编号. If unset, the login response's tenantId is used. |

### Product-Image Harvest

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `CRAWLER_HARVEST_ENABLED` | No | `true` | Enables headless browser product-image rendering. Set `false` only if product-image harvest should be disabled. |
| `CRAWLER_BROWSERLESS_WS` | No | `ws://127.0.0.1:3010` | Host-side Browserless WebSocket URL. Containers override this to `ws://browserless:3000`. |
| `BROWSERLESS_TOKEN` | Yes if harvest enabled | `pod-radar-browserless` | Browserless token. |
| `HIHUMBIRD_FACTORY_SLUG` | No | `fnsz-sale` | Factory route slug used by the merchant page. |
| `HIHUMBIRD_FACTORY_LIST_URL` | No | `https://flyshark.merchant.hihumbird.com/factory/fnsz-sale/produceManage/produceItemsManage` | Override the factory production-list page URL. |
| `HIHUMBIRD_FACTORY_SESSION_JSON` | No | `{...}` | Optional localStorage/cookie dump for factory page session helpers. Runtime login still refreshes token. |
| `CRAWLER_HARVEST_RENDER_WAIT_MS` | No | `12000` | Max render wait per page. |
| `CRAWLER_HARVEST_MAX_PAGES` | No | `500` | Safety cap for page traversal. |
| `CRAWLER_HARVEST_QUIET_MS` | No | `60000` | Idle time with no new images before a harvest page is considered done. |
| `DOCKER_CRAWLER_HARVEST_SCREENSHOT_DIR` | No | `/app/logs/harvest-shots` | Set to save harvest screenshots from inside the container. Empty disables screenshots. |

### Worker Tuning

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `HIHUMBIRD_FETCHER_REPLICAS` | No | `6` | Number of `pod-radar-hihumbird-fetcher` containers (matches the PM2 ecosystem's 6 instances). |
| `HIHUMBIRD_FETCHER_BATCH` | No | `8` | Rows claimed per hihumbird fetcher loop. |
| `HIHUMBIRD_FETCHER_CONCURRENCY` | No | `8` | Parallel downloads inside one `pod-radar-hihumbird-fetcher` container. Total download parallelism is replicas multiplied by this value. |
| `HIHUMBIRD_FETCHER_MEM_LIMIT` | No | `1500m` | Memory cap per normal hihumbird fetcher container; docker reclaims a bloated worker when it tops out. |
| `HIHUMBIRD_FETCHER_MAX_ATTEMPTS` | No | `2` | Max claim attempts for the normal fetcher. An oversized (272MP-class) image OOM-crashes it; after this many attempts it stops claiming the row so the high-memory fetcher takes over. Keep low. |
| `HIHUMBIRD_FETCHER_HIGHMEM_LIMIT` | No | `4096m` | Memory cap for the single high-memory fetcher (`~2.7×` normal) — holds a big image's full sharp decode (~1GB+). |
| `HIHUMBIRD_HIGHMEM_BATCH` | No | `4` | Rows claimed per loop by the high-memory fetcher. |
| `HIHUMBIRD_HIGHMEM_CONCURRENCY` | No | `2` | Parallel downloads in the high-memory fetcher. Kept low so multiple big images don't fill 4G at once. |
| `FANGGUO_FETCHER_REPLICAS` | No | `2` | Number of `pod-radar-fangguo-fetcher` containers. |
| `FANGGUO_FETCHER_BATCH` | No | `8` | Rows claimed per fangguo fetcher loop. |
| `FANGGUO_FETCHER_CONCURRENCY` | No | `8` | Parallel downloads inside one fangguo fetcher. Total = replicas × this value. |
| `FANGGUO_FETCHER_MEM_LIMIT` | No | `1500m` | Memory cap per fangguo fetcher container. |
| `CRAWLER_IMAGE_PROCESS_PIXEL_LIMIT` | No | `false` | Decoded `width × height` pixel limit for hihumbird assets. `false` disables sharp's built-in `268402689` pixel guard for trusted print/source images. Use a number to re-enable a hard limit. |
| `CRAWLER_HISTORY_ORDER_DAYS` | No | `90` | Orders whose item create time is older than this many days only crawl production + source images; product images (headless harvest) and shipping labels are skipped. Forward-only: applies to runs created after the change (tagged `params.history_gate`); existing runs are unaffected. |

## Common Commands

| Task | Command |
| --- | --- |
| Pull configured image | `docker compose -f compose.crawler.yml pull` |
| Start or update | `docker compose -f compose.crawler.yml up -d` |
| Stop | `docker compose -f compose.crawler.yml down` |
| View status | `docker compose -f compose.crawler.yml ps` |
| API logs | `docker compose -f compose.crawler.yml logs -f pod-radar-crawler-api` |
| Sync logs | `docker compose -f compose.crawler.yml logs -f pod-radar-hihumbird-sync` |
| Fetcher logs | `docker compose -f compose.crawler.yml logs -f pod-radar-hihumbird-fetcher` |
| Harvest logs | `docker compose -f compose.crawler.yml logs -f pod-radar-hihumbird-harvest browserless` |
| Change hihumbird fetcher replicas | Edit `HIHUMBIRD_FETCHER_REPLICAS`, then run `docker compose -f compose.crawler.yml up -d` |

## Notes

- Buckets are not created by Docker. Create `CRAWLER_S3_BUCKET_IMAGES` and `CRAWLER_S3_BUCKET_DOCS` in your external object store.
- Product images may remain 403 until the harvest worker opens the hihumbird factory page and triggers frontend rendering.
- The harvest worker defaults to the hihumbird `历史生产项` tab so both recent and older production items can be rendered by production-item code.
- `.env` in this directory is the deployment env file. `CRAWLER_DATABASE_URL` in it is useful for host-side scripts; the compose file sets the container-side database URL to the internal service name.
- `./logs` is relative to this deployment directory.
- Do not put object-storage credentials in the Dockerfile. Runtime configuration belongs in `.env` or your deployment secret manager.
- Prefer one process per container. Use `HIHUMBIRD_FETCHER_REPLICAS` for more fetcher containers; do not run PM2 inside these worker containers unless your hosting platform cannot scale services.
