# DD_Rag 部署记录

本文记录当前 DD_Rag 项目的两种安装方式：

- 分开安装：两台 Linux 服务器拆分部署。
- 一次安装：单台机器使用完整 `docker-compose.yml` 一次启动。

## 一、分开安装

适用于两台小内存服务器，例如每台约 `3.3G` 内存。

### 1. 服务器规划

服务器 A：`114.132.62.232`

- PostgreSQL
- Elasticsearch
- MinIO
- Elasticvue

服务器 B：`106.55.44.191`

- Ollama
- 后端 Backend
- 前端 Frontend

对应文件：

- `deploy/two-node/docker-compose.server-a.yml`
- `deploy/two-node/docker-compose.server-b.yml`
- `deploy/two-node/server-a.env.example`
- `deploy/two-node/server-b.env.example`

### 2. 上传项目

本地建议先复制一份精简目录，例如：

```powershell
E:\AICode\ddrag
```

然后上传到两台服务器：

```powershell
scp -r E:\AICode\ddrag root@114.132.62.232:/opt/
scp -r E:\AICode\ddrag root@106.55.44.191:/opt/
```

两台服务器分别整理目录：

```bash
mv /opt/ddrag /opt/dd-rag
cd /opt/dd-rag
ls
```

### 3. 服务 A 配置

```bash
cd /opt/dd-rag/deploy/two-node
cp server-a.env.example .env.server-a
```

服务 A 的 PostgreSQL 对外端口是 `5433`，因为 compose 中是：

```yaml
ports:
  - "${POSTGRES_PORT:-5433}:5432"
```

### 4. 服务 A 使用本地导出的 ES 镜像

如果服务器构建 Elasticsearch 很慢，可以本地导出已经安装 IK 插件的镜像。

本地 Windows：

```powershell
New-Item -ItemType Directory -Force E:\AICode\docker-export
docker save -o "E:\AICode\docker-export\dd-rag-elasticsearch.tar" dd_rag-elasticsearch:latest
scp "E:\AICode\docker-export\dd-rag-elasticsearch.tar" root@114.132.62.232:/opt/dd-rag/
```

服务 A：

```bash
cd /opt/dd-rag
docker load -i dd-rag-elasticsearch.tar
docker images
```

如果使用导入镜像，服务 A 的 `docker-compose.server-a.yml` 中 `elasticsearch` 应使用：

```yaml
elasticsearch:
  image: dd_rag-elasticsearch:latest
```

不要再使用：

```yaml
build:
  context: ../..
  dockerfile: docker/elasticsearch/Dockerfile
```

### 5. 启动服务 A

```bash
cd /opt/dd-rag/deploy/two-node
docker compose --env-file .env.server-a -f docker-compose.server-a.yml up -d
```

查看状态：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml ps
```

验证：

```bash
curl http://127.0.0.1:9200
curl http://127.0.0.1:9000/minio/health/live
```

### 6. 服务 B 配置

```bash
cd /opt/dd-rag/deploy/two-node
cp server-b.env.example .env.server-b
vim .env.server-b
```

关键配置：

```env
POSTGRES_HOST=114.132.62.232
POSTGRES_INTERNAL_PORT=5433

MINIO_HOST=114.132.62.232
MINIO_INTERNAL_PORT=9000

ELASTICSEARCH_HOST=114.132.62.232
ELASTICSEARCH_PORT=9200

BACKEND_PORT=18080
FRONTEND_PORT=5173
VITE_API_BASE_URL=/api
VITE_DEV_PROXY_TARGET=http://backend:8080
```

说明：

- 浏览器访问前端：`http://106.55.44.191:5173`
- 前端请求 `/api/...`
- Vite dev server 将 `/api` 代理到 `backend:8080`
- 不建议让浏览器直接跨端口访问 `18080`，否则容易遇到 CORS 预检问题。

### 7. 启动服务 B

```bash
cd /opt/dd-rag/deploy/two-node
docker compose --env-file .env.server-b -f docker-compose.server-b.yml up -d
```

查看状态：

```bash
docker compose --env-file .env.server-b -f docker-compose.server-b.yml ps
```

查看日志：

```bash
docker compose --env-file .env.server-b -f docker-compose.server-b.yml logs -f backend
docker compose --env-file .env.server-b -f docker-compose.server-b.yml logs -f frontend
docker compose --env-file .env.server-b -f docker-compose.server-b.yml logs -f ollama-model-init
```

验证：

```bash
curl http://127.0.0.1:18080/actuator/health
curl http://127.0.0.1:5173
```

浏览器访问：

```text
http://106.55.44.191:5173
```

### 8. 端口开放

服务 A 只建议对服务 B 开放：

- `5433`：PostgreSQL
- `9200`：Elasticsearch
- `9000`：MinIO API

服务 B 对外开放：

- `5173`：前端页面
- `18080`：后端 API，当前同源代理方案下外部不必直接访问，但可保留用于健康检查。

如需临时调试：

- `9001`：MinIO Console
- `8088`：Elasticvue

### 9. Docker 镜像加速

Linux 服务器可配置 `/etc/docker/daemon.json`：

```json
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.1ms.run",
    "https://docker-0.unsee.tech",
    "https://docker.m.daocloud.io"
  ]
}
```

重启 Docker：

```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
docker info
```

## 二、一次安装

适用于单台机器内存较充足，或者本地开发环境。

对应文件：

```text
docker-compose.yml
```

### 1. 上传项目

```powershell
scp -r E:\AICode\ddrag root@服务器IP:/opt/
```

服务器：

```bash
mv /opt/ddrag /opt/dd-rag
cd /opt/dd-rag
```

### 2. 启动完整服务

```bash
cd /opt/dd-rag
docker compose up -d --build
```

如果 Elasticsearch 构建很慢，可以先本地导出 `dd_rag-elasticsearch:latest`，再在服务器导入，并把 `docker-compose.yml` 中的 `elasticsearch` 从 `build` 改为：

```yaml
elasticsearch:
  image: dd_rag-elasticsearch:latest
```

### 3. 查看状态

```bash
docker compose ps
```

### 4. 查看日志

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f elasticsearch
docker compose logs -f minio
docker compose logs -f postgres
```

### 5. 验证接口

```bash
curl http://127.0.0.1:9200
curl http://127.0.0.1:9000/minio/health/live
curl http://127.0.0.1:18080/actuator/health
curl http://127.0.0.1:5173
```

浏览器访问：

```text
http://服务器IP:5173
```

### 6. 停止服务

```bash
docker compose down
```

如果需要连数据卷一起删除：

```bash
docker compose down -v
```

## 三、常见问题记录

### 1. `docker compose down` 提示 `no configuration file provided`

原因：当前目录没有 compose 文件。

处理：

```bash
cd /opt/dd-rag
docker compose down
```

或显式指定：

```bash
docker compose -f /opt/dd-rag/docker-compose.yml down
```

### 2. Elasticsearch 报 `ik_max_word` 不存在

现象：

```text
Custom Analyzer [ddrag_ik_index] failed to find tokenizer under name [ik_max_word]
```

原因：Elasticsearch 镜像没有安装 IK 分词插件。

处理：

- 使用 `docker/elasticsearch/Dockerfile` 构建自定义镜像。
- 或使用本地已经构建好的 `dd_rag-elasticsearch:latest` 导出再导入服务器。

### 3. 前端登录出现 `OPTIONS 403` 或网络错误

原因：浏览器从 `5173` 直接请求 `18080`，属于跨源请求，后端没有放行 CORS 预检。

当前推荐处理：

```env
VITE_API_BASE_URL=/api
VITE_DEV_PROXY_TARGET=http://backend:8080
```

让浏览器请求：

```text
http://服务BIP:5173/auth/login
```

或：

```text
http://服务BIP:5173/api/...
```

具体以当前前端代理配置为准，原则是避免浏览器直接跨端口访问后端。

### 4. 查看容器日志

双机部署：

```bash
docker compose --env-file .env.server-b -f docker-compose.server-b.yml logs --tail=100 backend
docker compose --env-file .env.server-b -f docker-compose.server-b.yml logs --tail=100 frontend
```

一次安装：

```bash
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
```
