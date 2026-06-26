# pod-radar Nginx 80 反向代理

对外只暴露 `80`，内部服务只监听 `127.0.0.1`：

| 外部路径 | 内部服务 |
|---|---|
| `/` | main web `127.0.0.1:5174` |
| `/api/*` | main backend `127.0.0.1:3001` |
| `/docs`, `/openapi.json` | main backend `127.0.0.1:3001` |

## 系统 Nginx

```bash
sudo cp deploy/nginx/pod-radar.conf /etc/nginx/conf.d/pod-radar.conf
sudo nginx -t
sudo systemctl reload nginx
```

## Docker Nginx

```bash
docker compose -f docker-compose.nginx.yml up -d
```

## 应用重启

Nginx 起好后，重启 PM2 让服务改为 localhost-only：

```bash
pm2 restart pod-radar-backend pod-radar-web --update-env
pm2 save
```
