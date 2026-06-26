# Main Docker Deployment

This guide covers `compose.yml`, the main image-search system. Treat this directory as a standalone deployment repository.

The Docker stack does not start any object storage. Configure an external OSS / S3-compatible object store with `S3_*` variables and create the buckets before starting workers.

## Services

| Service | Purpose |
| --- | --- |
| `pgvector` | Main PostgreSQL / TimescaleDB database. |
| `main-db-push` | Applies the main database schema at startup. |
| `pod-radar-backend` | Main API service. |
| `pod-radar-web` | Main web UI. |
| `pod-radar-fetcher` | Downloads and stores source images. |
| `pod-radar-embed-worker` | Embedding worker. Can stay idle with `EMBEDDING_ENABLED=false`. |
| `pod-radar-search-worker` | Search / duplicate-review worker. |

## Deploy

1. Copy the example env in this deployment directory:

   ```bash
   cp compose.env.example .env
   ```

2. Edit `.env`:

   - Set `MAIN_IMAGE` to the image tag you want to deploy.
   - Set a strong `POSTGRES_PASSWORD`.
   - Fill `S3_ENDPOINT`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, and bucket names.
   - Create the S3 buckets before starting the workers.
   - Set `BOOTSTRAP_ADMIN_KEY`.
   - Set embedding variables if `EMBEDDING_ENABLED=true`.

3. Start:

   ```bash
   docker compose -f compose.yml pull
   docker compose -f compose.yml up -d
   ```

   To run multiple worker containers, set `MAIN_FETCHER_REPLICAS`, `MAIN_EMBED_REPLICAS`, and `MAIN_SEARCH_REPLICAS` in `.env`. Do not run PM2 inside the worker containers.

4. Check status and logs:

   ```bash
   docker compose -f compose.yml ps
   docker compose -f compose.yml logs -f pod-radar-backend
   ```

5. Open the web UI:

   ```text
   http://<server-host>:5174
   ```

## Environment Variables

### Host Ports

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `MAIN_IMAGE` | No | `codedevin/pod-radar-main:v1.0.0` | Application image used by backend, web, and main workers. |
| `POSTGRES_HOST_BIND` | No | `0.0.0.0` | Host interface for the database port. |
| `POSTGRES_HOST_PORT` | No | `5544` | Host port mapped to the main database. |
| `BACKEND_HOST_BIND` | No | `0.0.0.0` | Host interface for the backend API. |
| `BACKEND_PORT` | No | `3001` | Host port for `pod-radar-backend`. |
| `WEB_HOST_BIND` | No | `0.0.0.0` | Host interface for the web UI. |
| `WEB_PORT` | No | `5174` | Host port for `pod-radar-web`. |

### Database

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `POSTGRES_USER` | Yes | `pod_radar` | Database user created in the `pgvector` container. |
| `POSTGRES_PASSWORD` | Yes | `pod_radar_pass` | Database password. Change this in production. |
| `DATABASE_URL` | Yes | `postgres://pod_radar:pod_radar_pass@127.0.0.1:5544/pod_radar` | Host-side database URL for local CLI commands. Inside containers, compose overrides it to `pgvector:5432`. |

### External S3

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `S3_PROVIDER` | Yes | `s3` | Storage provider mode. Use `s3` for external S3-compatible storage. |
| `S3_URL_STYLE` | Yes | `virtual-hosted` | URL style: `virtual-hosted` for AWS-like S3, `path` for path-style endpoints. |
| `S3_ENDPOINT` | Yes | `https://s3.example.com` | External object-storage endpoint; API object URLs are built from this endpoint. |
| `S3_REGION` | Yes | `us-east-1` | S3 region. |
| `S3_ACCESS_KEY_ID` | Yes | `AKIA...` | S3 access key. |
| `S3_SECRET_ACCESS_KEY` | Yes | `...` | S3 secret key. |
| `S3_BUCKET_IMAGES` | Yes | `pod-radar-images` | Bucket for original images. |
| `S3_BUCKET_THUMBS` | Yes | `pod-radar-thumbs` | Bucket for thumbnails. |
| `S3_BUCKET_ACCESS` | Yes | `public` | `public` returns direct object URLs; `private` returns presigned URLs. |

### Bootstrap Admin

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `BOOTSTRAP_NAME` | Yes | `admin` | Initial admin name. |
| `BOOTSTRAP_ADMIN_KEY` | Yes | `pod-radar_replace_with_a_long_random_value` | Initial admin key. Must start with `pod-radar_` and be at least 20 characters. |

### Web And Search

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `NEXT_PUBLIC_SEARCH_MIN_SCORE` | No | `0.5` | Public search threshold used at build time. |
| `SEARCH_MIN_SCORE` | No | `0.5` | Runtime search threshold. |
| `SEARCH_DUPLICATE_REVIEW_ENABLED` | No | `true` | Enables duplicate-review workflow. |
| `SEARCH_DUPLICATE_REVIEW_SCORE` | No | `0.98` | Score threshold for duplicate review. |
| `IMAGE_TOKEN_TTL` | No | `900` | Image URL token TTL in seconds. |
| `IMAGE_TOKEN_SECRET` | No | `replace_with_a_long_random_value` | Explicit image-token secret. If unset, a deterministic secret is derived from `DATABASE_URL`. |

### Docker Logs

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `DOCKER_LOG_MAX_SIZE` | No | `50m` | Max size of one Docker `json-file` log segment per container. |
| `DOCKER_LOG_MAX_FILE` | No | `10` | Number of rotated log segments per container. `50m × 10` caps each container at about 500M. |

### Embedding

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `EMBEDDING_ENABLED` | No | `true` | Runs embedding jobs by default. Set `false` to keep the embed worker idle. |
| `EMBEDDING_PROVIDER` | No | `dashscope` | Which HTTP client/endpoint to call at runtime. Switching providers (e.g. openai-compat ↔ dashscope native) keeps vectors compatible — no re-embed, no new table. |
| `EMBEDDING_MODEL` | No | `qwen3-vl-embedding` | Embedding model name. |
| `EMBEDDING_BASE_URL` | No | `https://dashscope.aliyuncs.com/...` | Embedding API endpoint. |
| `EMBEDDING_DIMENSION` | No | `2560` | Vector dimension expected by the database. |
| `EMBEDDING_TIMEOUT` | No | `30` | Embedding request timeout in seconds. |
| `EMBEDDING_API_KEY` | Required if embeddings enabled | `...` | Provider API key. |
| `EMBEDDING_SLUG` | No | `qwen3_vl_embedding` | Optional override for the table slug. Defaults to the model name (no provider prefix); the vector table is `image_emb_<slug>_<dim>`. |
| `EMBEDDING_TEXT_PRICE` | No | `0.0000007` | Text per-unit price seeded into the models row at `db:push` (per-token = price per 1M ÷ 1e6). Empty = no cost tracking. |
| `EMBEDDING_IMAGE_PRICE` | No | `0.0000018` | Image per-unit price (vision tokens). Empty falls back to the text price. |
| `EMBEDDING_PRICE_UNIT` | No | `token` | Unit the prices are charged against (`token`, `image`, or `second`). |
| `EMBEDDING_CURRENCY` | No | `CNY` | Currency for the seeded prices. Defaults to `USD`. |

### Worker Tuning

| Variable | Required | Example | Description |
| --- | --- | --- | --- |
| `MAIN_FETCHER_REPLICAS` | No | `2` | Number of `pod-radar-fetcher` containers. |
| `MAIN_FETCHER_BATCH` | No | `8` | Rows claimed per fetcher loop. |
| `MAIN_FETCHER_CONCURRENCY` | No | `8` | Parallel downloads inside one `pod-radar-fetcher` container. Total download parallelism is replicas multiplied by this value. |
| `FETCHER_HTTP_TIMEOUT` | No | `60` | Original image HTTP download timeout in seconds. Large/slow source images should keep this at 60s or higher. |
| `MAIN_EMBED_REPLICAS` | No | `2` | Number of `pod-radar-embed-worker` containers. |
| `MAIN_EMBED_BATCH` | No | `8` | Rows claimed per embed-worker loop. |
| `MAIN_EMBED_CONCURRENCY` | No | `4` | Parallel embedding requests inside one embed-worker container. |
| `MAIN_SEARCH_REPLICAS` | No | `2` | Number of `pod-radar-search-worker` containers. |
| `MAIN_SEARCH_BATCH` | No | `4` | Rows claimed per search-worker loop. |
| `MAIN_SEARCH_CONCURRENCY` | No | `4` | Parallel search jobs inside one search-worker container. |

## Common Commands

| Task | Command |
| --- | --- |
| Pull configured image | `docker compose -f compose.yml pull` |
| Start or update | `docker compose -f compose.yml up -d` |
| Stop | `docker compose -f compose.yml down` |
| View status | `docker compose -f compose.yml ps` |
| Backend logs | `docker compose -f compose.yml logs -f pod-radar-backend` |
| Worker logs | `docker compose -f compose.yml logs -f pod-radar-fetcher pod-radar-embed-worker pod-radar-search-worker` |
| Change worker replicas | Edit `MAIN_FETCHER_REPLICAS`, `MAIN_EMBED_REPLICAS`, or `MAIN_SEARCH_REPLICAS`, then run `docker compose -f compose.yml up -d` |

## Notes

- Buckets are not created by Docker. Create `S3_BUCKET_IMAGES` and `S3_BUCKET_THUMBS` in your external object store.
- `.env` in this directory is the deployment env file. `DATABASE_URL` in it is useful for host-side scripts; the compose file sets the container-side database URL to the internal service name.
- `./logs` is relative to this deployment directory.
- Do not put object-storage credentials in the Dockerfile. Runtime configuration belongs in `.env` or your deployment secret manager.
- Prefer one process per container. Use the `*_REPLICAS` env variables for more worker containers; do not run PM2 inside these worker containers unless your hosting platform cannot scale services.
