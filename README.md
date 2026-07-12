# DD_Rag

DD_Rag 是一个面向组织知识库的全栈 RAG 与 AI 助手项目。它将用户与组权限、文档生命周期、混合检索、证据约束问答、会话式 Agent 和模型治理组合为一条可运行的工程闭环。

当前版本为 V6.1：模型连接、模型授权和调用治理不再依赖部署期固定的 Chat API Key，而是由平台管理员或用户在应用内配置。

## 在线体验

当前可用演示入口：[http://114.132.62.232:5173/login](http://114.132.62.232:5173/login)

## 项目能力

- 组织知识库：`ADMIN / USER` 系统角色与 `OWNER / MEMBER` 组内角色分离。
- 文档全生命周期：上传、解析、清洗、切片、异步入库、状态追踪与删除。
- 混合检索问答：PgVector 语义召回、Elasticsearch 关键词召回、RRF 融合和证据约束回答。
- Assistant：多轮会话、流式响应、短期记忆摘要与 `KB_SEARCH` 工具调用。
- 模型平台：平台共享模型、用户 BYOK、模型发现/测试、场景路由、个人指令和调用账本。
- 调用治理：会话互斥、全局/用户/连接并发限制、超时、取消与失败状态记录。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot、Spring AI、Spring AI Alibaba、MyBatis-Plus、Flyway |
| 前端 | Vue 3、TypeScript、Vite、Pinia |
| 数据与检索 | PostgreSQL + pgvector、Elasticsearch + IK、MinIO |
| 本地模型能力 | Ollama（默认用于 Embedding） |
| 部署 | Docker Compose（单机） |

## 架构概览

```text
Vue 3 Frontend
      |
      v
Spring Boot API
  ├─ Auth / User / Admin
  ├─ GroupMembership
  ├─ Document / Ingestion
  ├─ QA: PgVector + Elasticsearch + RRF
  ├─ Assistant: Session + Memory + KB_SEARCH
  └─ Model Platform
       ├─ 连接、模型、授权、场景路由
       ├─ Provider 适配：DashScope / OpenAI / Gemini / Anthropic
       └─ 调用账本、并发、超时与取消
      |
      ├─ PostgreSQL + pgvector
      ├─ Elasticsearch
      ├─ MinIO
      └─ Ollama
```

## 核心链路

### 文档入库

```text
上传文档
-> 校验组权限
-> MinIO 保存原文
-> 解析、清洗与切片
-> PostgreSQL 保存文档与 chunk
-> PgVector 写入向量
-> Elasticsearch 写入关键词索引
-> 文档进入 READY 状态
```

### RAG 问答

```text
用户问题 + groupId
-> 校验组可读权限
-> 查询规划
-> PgVector 语义召回 + Elasticsearch 关键词召回
-> RRF 融合与证据组织
-> 基于证据回答，或在证据不足时拒答
-> 返回答案与引用文件
```

### Assistant 与模型调用

```text
用户消息
-> 会话与短期记忆上下文
-> 解析会话模型 / 用户指令 / 场景路由
-> 授权与并发许可
-> Provider ChatModel 调用
-> 可选 KB_SEARCH 工具观察
-> 流式返回、消息持久化与调用账本
```

## 模型配置

应用启动时**不需要**配置 Chat Provider API Key，也不会从 YAML 读取默认 Chat 模型。

启动后按实际使用方式配置：

1. 平台管理员访问 `/admin/model-governance`，创建平台模型连接、测试模型、授予用户权限并配置场景路由。
2. 普通用户访问 `/ai-settings`，可管理自己的 BYOK 连接、个人 Assistant 指令和用量记录。
3. 在 Assistant 会话中选择被授权且已测试的模型后再发起对话。

> 当前版本的模型连接密钥保存在数据库中，查询接口只返回掩码。请使用受控数据库、限制访问权限，且不要将真实密钥写入 Git、日志或 README。

## 单机 Docker Compose 部署

这是本仓库唯一保留的部署方式。根目录 `docker-compose.yml` 会在同一台机器启动 PostgreSQL、Elasticsearch、MinIO、Ollama、后端、前端和 Elasticvue。

### 前置条件

- Docker Desktop 或 Docker Engine
- Docker Compose v2
- 可访问 Docker Hub 与 Ollama 模型下载源

首次启动会拉取容器镜像，并下载 `qllama/bge-small-zh-v1.5` Embedding 模型，耗时取决于网络与磁盘情况。

### 启动

在 PowerShell 中执行：

```powershell
cd E:\AICode\DD_Rag_clean\DD_Rag
docker compose up -d --build
docker compose ps
```

不需要设置 `DASHSCOPE_API_KEY`。模型 API Key 仅在管理员或用户通过应用页面创建模型连接时提供。

查看后端启动、Flyway 迁移和健康检查日志：

```powershell
docker compose logs -f backend
```

### 访问地址

| 服务 | 地址 |
| --- | --- |
| 前端（请从这里访问应用） | http://localhost:5173 |
| 后端健康检查（调试用） | http://localhost:18080/actuator/health |
| PostgreSQL | `localhost:5433` |
| MinIO Console | http://localhost:9001 |
| Elasticvue | http://localhost:8088 |
| Ollama | http://localhost:11434 |

日常使用请打开 **前端** `http://localhost:5173`。浏览器内的 API 请求走同源路径 `/api/*`，由 Vite 开发服务器代理到后端容器，**不要**在浏览器里直连 `http://localhost:18080` 做页面联调。

### 前后端联调与 CORS（重要）

Compose 默认约定：

| 项 | 值 | 说明 |
| --- | --- | --- |
| 浏览器 Origin | `http://localhost:5173` | 只访问前端 |
| 前端 `baseURL` | `/api`（默认，不设 `VITE_API_BASE_URL`） | 同源，避免跨域 |
| Vite 代理目标 | `VITE_DEV_PROXY_TARGET=http://backend:8080` | **容器内**用服务名 `backend`，不能写 `localhost` |
| 后端映射端口 | 主机 `18080` → 容器 `8080` | 仅健康检查 / 直接调 API 时用 |

错误示例（会触发浏览器 CORS）：

```yaml
# 错误：让浏览器直连主机上的后端端口
VITE_API_BASE_URL: http://localhost:18080
```

这样会出现类似：

```text
Access to XMLHttpRequest at 'http://localhost:18080/auth/login'
from origin 'http://localhost:5173' has been blocked by CORS policy
```

原因：

1. 前端页在 `5173`，API 打到 `18080`，属于跨域；当前后端**未配置** CORS 响应头，预检失败。
2. 正确登录路径是 `/api/auth/login`。若 `baseURL` 写成裸主机地址且前端再拼 `/auth/login`，还会少一层 `/api`。
3. 前端容器里的 Vite 代理必须指向 Compose 服务名 `http://backend:8080`。写 `http://localhost:8080` 会指向前端容器自身，代理 502。

正确示例（与当前 `docker-compose.yml` 一致）：

```yaml
frontend:
  environment:
    VITE_DEV_PROXY_TARGET: http://backend:8080
    # 不要设置 VITE_API_BASE_URL=http://localhost:18080
```

请求路径：

```text
浏览器  →  http://localhost:5173/api/auth/login   （同源）
Vite    →  http://backend:8080/api/auth/login     （容器网络）
```

修改 frontend 环境变量后需重建前端容器：

```powershell
docker compose up -d --force-recreate frontend
```

然后在浏览器对 `http://localhost:5173` 做一次硬刷新（Ctrl+F5）。

### 数据库迁移与数据持久化

后端启动时由 Flyway 自动执行 `V1` 至 `V16` 迁移，其中 V15/V16 创建并扩展 V6.1 模型平台表。

停止服务但保留 PostgreSQL、Elasticsearch、MinIO 和 Ollama 数据卷：

```powershell
docker compose down
```

> 不要随意执行 `docker compose down -v`。该命令会删除数据卷，导致数据库、文档对象、检索索引和已下载模型丢失。

## 项目结构

```text
DD_Rag
├─ frontend                         # Vue 3 前端
├─ src/main/java/com/dong/ddrag
│  ├─ auth / user / identity        # 登录、用户与身份上下文
│  ├─ groupmembership               # 组、成员与权限
│  ├─ document / ingestion          # 文档与入库任务
│  ├─ retrieval / qa                # 检索与问答
│  ├─ assistant                     # 会话、记忆与 Agent
│  ├─ modelplatform                 # 模型连接、授权、运行时治理
│  └─ storage                       # MinIO 存储
├─ src/main/resources
│  ├─ db/migration                  # Flyway 迁移
│  ├─ mapper                        # MyBatis XML
│  └─ prompts                       # Prompt 模板
├─ docker                            # Elasticsearch IK 镜像
├─ docker-compose.yml                # 单机部署编排
└─ docs/V6.1                        # 模型平台设计与验收记录
```

## 阅读路线

- `docs/PROJECT_READING_GUIDE.md`：项目代码阅读路线。
- `harness/modules/`：核心业务闭环的 Harness 约束与测试入口。

## 开发与安全提醒

- `KB_SEARCH` 是 Assistant 可调用工具，不是 QA Service 的硬编码模式分支。
- Assistant 采用短期记忆与会话摘要；不使用自动长期记忆主链。
- 普通业务接口必须同时满足系统角色与组内权限边界。
- 模型密钥、数据库密码和 JWT 密钥不得提交到 Git。
- Docker 本地联调优先使用 Vite 同源代理（`/api` → `backend:8080`），不要把 `VITE_API_BASE_URL` 设成跨域的 `http://localhost:18080`。
- 当前 Compose 以单机自托管和验证为目标；如需生产级高可用、密钥托管或多实例部署，应另行设计。

## 社区支持
https://linux.do/
