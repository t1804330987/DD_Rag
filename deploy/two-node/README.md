# 双机 Docker Compose 部署

这套文件用于两台 `3.3G` 级别 Linux 服务器的拆分部署，**不会覆盖根目录现有的完整** [docker-compose.yml](/E:/AICode/DD_Rag/docker-compose.yml)。

## 目录说明

- `docker-compose.server-a.yml`
  - 服务器 A：`postgres`、`elasticsearch`、`minio`、`elasticvue`
- `docker-compose.server-b.yml`
  - 服务器 B：`ollama`、`backend`、`frontend`
- `server-a.env.example`
  - 服务器 A 环境变量示例
- `server-b.env.example`
  - 服务器 B 环境变量示例

## 推荐分工

### 服务器 A

- PostgreSQL
- Elasticsearch
- MinIO

### 服务器 B

- Ollama
- Backend
- Frontend

## 前置要求

两台机器都需要：

```bash
docker --version
docker compose version
```

两台机器上都建议把项目代码拉到相同目录，例如：

```bash
mkdir -p /opt/dd-rag
cd /opt/dd-rag
git clone <your-repo-url> .
```

## 环境变量准备

### 服务器 A

```bash
cd /opt/dd-rag/deploy/two-node
cp server-a.env.example .env.server-a
```

### 服务器 B

```bash
cd /opt/dd-rag/deploy/two-node
cp server-b.env.example .env.server-b
```

然后把 `.env.server-b` 里的这几个值改成真实内网地址：

- `POSTGRES_HOST`
- `MINIO_HOST`
- `ELASTICSEARCH_HOST`
- `VITE_API_BASE_URL`

## 启动顺序

### 第一步：启动服务器 A

```bash
cd /opt/dd-rag/deploy/two-node
docker compose --env-file .env.server-a -f docker-compose.server-a.yml up -d --build
```

检查状态：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml ps
```

### 第二步：启动服务器 B

```bash
cd /opt/dd-rag/deploy/two-node
docker compose --env-file .env.server-b -f docker-compose.server-b.yml up -d
```

检查状态：

```bash
docker compose --env-file .env.server-b -f docker-compose.server-b.yml ps
```

## 验证顺序

### 服务器 A

```bash
curl http://127.0.0.1:9200
curl http://127.0.0.1:9000/minio/health/live
docker compose --env-file .env.server-a -f docker-compose.server-a.yml logs -f elasticsearch
```

### 服务器 B

```bash
curl http://127.0.0.1:18080/actuator/health
curl http://127.0.0.1:5173
docker compose --env-file .env.server-b -f docker-compose.server-b.yml logs -f backend
```

## 防火墙建议

### 服务器 A 对服务器 B 开放

- `5432`
- `9200`
- `9000`

### 服务器 B 对外开放

- `18080`
- `5173`
- 如需远程调试 Ollama，再开放 `11434`

## 说明

这套仍然是“开发态运行”：

- backend 还是 `spring-boot:run`
- frontend 还是 `npm run dev`

优点是接近你当前本地链路，迁移最简单。

如果后面要正式上生产，建议再单独补：

- 后端生产 Dockerfile
- 前端静态构建 + Nginx
- 专用 `docker-compose.prod.*.yml`

## 停止命令

### 服务器 A

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml down
```

### 服务器 B

```bash
docker compose --env-file .env.server-b -f docker-compose.server-b.yml down
```
