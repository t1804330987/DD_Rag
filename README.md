# DD_Rag

> 一个面向“组织知识库 + AI 助手”的 Java RAG 实战项目。它不是简单的 ChatGPT 套壳，而是把权限隔离、文档入库、混合检索、证据约束、Agent 工具调用和 Docker 部署串成了一条完整工程链路。

如果你正在找一个能写进简历、能讲清架构、能覆盖 Spring AI / Spring AI Alibaba 技术点的项目，DD_Rag 值得 Star。

## 在线体验

当前可用演示入口：

```text
http://106.55.44.191:5173/login
```

## 为什么做这个项目

从第一性原理看，企业知识库问答至少要解决 4 个问题：

1. **知识从哪里来**：文档上传后要能解析、清洗、切片、存储和索引。
2. **谁能看什么**：知识库不是公共聊天室，必须按组织和成员权限隔离。
3. **回答凭什么可信**：模型不能只靠想象回答，必须基于可追溯证据。
4. **对话如何持续**：真实助手不是一次性问答，需要会话恢复、上下文压缩和工具调用。

DD_Rag 就是围绕这 4 个问题设计的。它把 RAG 从“能跑 demo”推进到“能解释工程边界”的状态。

## 项目亮点

- **Spring AI 1.1.2 实战**：使用 `ChatClient`、`PromptTemplate`、`RetrievalAugmentationAdvisor`、`DocumentRetriever`、`VectorStore`、PgVector、Ollama 等能力构建 RAG 主链。
- **Spring AI Alibaba 1.1.2.0 实战**：接入 DashScope 和 `spring-ai-alibaba-agent-framework`，用 `ReactAgent` 承接多轮助手和工具调用。
- **组织级知识库隔离**：系统角色 `ADMIN / USER` 与组内角色 `OWNER / MEMBER` 分离，避免“登录了就能看全部数据”的伪权限设计。
- **混合检索架构**：PgVector 语义召回 + Elasticsearch 关键词召回 + RRF 融合，兼顾语义相似和关键词精确命中。
- **证据约束回答**：检索不到证据时拒答，检索到证据时让模型基于上下文回答，降低幻觉。
- **Assistant Tool 化知识库检索**：`KB_SEARCH` 被封装为 Agent 可调用工具，而不是在 Service 里硬编码分支。
- **短期记忆与会话摘要**：通过消息落库、摘要维护和 `beforeModel` 上下文重组控制长对话输入。
- **完整前后端闭环**：Vue 3 + Spring Boot 3.5 + PostgreSQL + MinIO + Elasticsearch + Ollama + Docker Compose。
- **适合面试讲解**：权限模型、RAG 链路、Agent 工具循环、模板渲染、Docker 双机部署都有真实工程问题和排查记录。

## 技术栈

### 后端

- Java 21
- Spring Boot 3.5.0
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.0
- Spring AI Alibaba Agent Framework
- MyBatis-Plus 3.5.12
- PostgreSQL + pgvector
- Elasticsearch 8.15.3 + IK Analyzer
- MinIO
- Flyway
- Knife4j / OpenAPI
- JWT + Refresh Token

### 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- 原生 CSS 模块化页面样式

### AI 能力

- DashScope Chat Model
- Ollama Embedding Model
- PgVector 向量检索
- Elasticsearch BM25 检索
- ReAct Agent
- Tool Calling
- 会话摘要与短期记忆

## 系统架构

```text
用户 / 管理员
  |
  v
Vue 3 前端工作台
  |
  v
Spring Boot API
  |
  +--> Auth / User / Admin
  |
  +--> GroupMembership
  |      +--> 系统角色 ADMIN / USER
  |      +--> 组内角色 OWNER / MEMBER
  |
  +--> Document
  |      +--> MinIO 原文存储
  |      +--> Parser 文档解析
  |      +--> Cleaner 文本清洗
  |      +--> Chunker 结构化切片
  |      +--> PostgreSQL chunk 落库
  |      +--> PgVector 向量写入
  |      +--> Elasticsearch 关键词索引
  |
  +--> QA
  |      +--> QueryPlanning
  |      +--> PgVector Retrieval
  |      +--> Elasticsearch Retrieval
  |      +--> RRF Fusion
  |      +--> Evidence Bundle
  |      +--> Spring AI ChatClient
  |
  +--> Assistant
         +--> Spring AI Alibaba ReactAgent
         +--> ShortTermMemoryHook
         +--> KnowledgeBaseSearchTool
         +--> QA 主链复用
```

## 核心链路

### 1. 文档入库链路

```text
上传文件
-> 校验用户是否拥有当前组权限
-> 文件写入 MinIO
-> 创建 document 和 ingestion job
-> 按文件类型选择 Parser
-> 清洗文本
-> 结构化切片
-> chunk 写入 PostgreSQL
-> embedding 写入 PgVector
-> chunk 写入 Elasticsearch
-> 文档进入 READY 状态
```

这个链路适合面试讲“文档知识库从上传到可问答的完整生命周期”。

### 2. RAG 问答链路

```text
用户问题 + groupId
-> 校验组可读权限
-> QueryPlanningService 生成 DIRECT / REWRITE / DECOMPOSE
-> PgVector 语义召回
-> Elasticsearch 关键词召回
-> RRF 融合候选
-> 同文档 chunk 聚簇与扩窗
-> EvidenceLevel 判断证据是否充分
-> 模型基于证据回答或拒答
-> 返回答案 + 引用文件
```

这里不是简单 `topK -> prompt -> answer`，而是把查询规划、混合召回、融合、证据组织和回答约束拆开处理。

### 3. Assistant 工具调用链路

```text
用户进入 /assistant
-> 新建或选择会话
-> 用户消息落库
-> 短期记忆维护会话摘要
-> ReactAgent 开始推理
-> AssistantShortTermMemoryHook 重组上下文
-> CHAT 直接对话
-> KB_SEARCH 调用 KnowledgeBaseSearchTool
-> Tool 返回检索证据
-> 模型基于工具观察生成最终回答
-> 助手消息和引用落库
```

这个链路可以讲清楚 Agent 不是“多包一层模型调用”，而是要处理工具上下文、工具观察、最大推理轮数和会话恢复。

## Spring AI 技术点

DD_Rag 中 Spring AI 不是只用一个聊天接口，而是覆盖了多个关键抽象：

- `ChatClient`：统一模型调用入口，用于 QA 回答和查询规划。
- `PromptTemplate`：管理提示词模板和变量渲染，处理 `{变量}` 与模板内容冲突问题。
- `VectorStore`：基于 PgVector 存储 embedding。
- `DocumentRetriever`：对外暴露 RAG 检索入口。
- `RetrievalAugmentationAdvisor`：将检索增强与模型调用组合。
- `Ollama`：用于本地 embedding 模型接入。

如果面试官问“你用 Spring AI 做了什么”，可以直接围绕这些抽象展开，而不是只说“调用了大模型 API”。

## Spring AI Alibaba 技术点

项目中 Spring AI Alibaba 主要承担两类能力：

- `spring-ai-alibaba-starter-dashscope`：接入 DashScope 模型能力。
- `spring-ai-alibaba-agent-framework`：使用 `ReactAgent` 构建多轮助手和工具调用流程。

重点设计：

- 将知识库检索封装为 `AssistantKnowledgeBaseTool`。
- 通过 `AssistantRunnableConfigFactory` 传入 `userId`、`sessionId`、`toolMode`、`groupId` 等运行上下文。
- 通过 `AssistantShortTermMemoryHook.beforeModel(...)` 重组模型输入。
- 通过 `recursionLimit` 控制工具最大推理轮数，避免工具循环调用。
- 工具返回的是检索结果和证据上下文，最终回答仍由模型基于工具观察生成。

这部分很适合展示“我不只是会 RAG，也理解 Agent 工具调用的上下文问题”。

## 功能模块

### 认证与用户

- 用户注册
- 登录 / 退出
- JWT access token
- refresh token 持久化与撤销
- 首次登录强制改密
- 管理员后台
- 用户状态管理

### 协作小组

- 创建组
- OWNER / MEMBER 权限
- 邀请成员
- 按组织 ID 申请加入
- OWNER 审批申请
- 成员列表与移除

### 文档中心

- 按组上传文档
- 文档列表
- 文档预览
- 文档删除
- 文档状态管理
- 文档入库链路

### 智能检索

- 组内问答
- 查询规划
- 语义召回
- 关键词召回
- 证据融合
- 无证据拒答
- 引用文件返回

### 个人智能助手

- 会话列表
- 多轮对话
- 流式响应
- 会话上下文恢复
- 短期记忆摘要
- 知识库工具调用

### 管理后台

- 管理员概览
- 用户列表
- 用户详情
- 用户状态调整
- 密码重置

## 为什么适合作为面试项目

很多 AI 项目只能展示“我调了模型接口”。DD_Rag 更适合面试，是因为它能回答更深的问题：

- 如何设计组织级知识库权限？
- 为什么 `ADMIN` 不应该直接拥有业务数据权限？
- 文档上传后如何变成可检索知识？
- 为什么只做向量检索不够？
- PgVector 和 Elasticsearch 如何融合？
- RRF 为什么比直接分数相加更稳？
- RAG 如何减少模型幻觉？
- 查询规划失败时如何降级？
- Tool 调用为什么会循环？如何限制？
- 工具返回证据还是直接返回答案？
- 长会话为什么不能直接全量塞进 prompt？
- Docker 部署中 Elasticsearch IK 插件为什么会影响上传链路？
- 前端为什么要通过同源代理避免 CORS 预检问题？

这些问题都来自真实工程链路，不是为了面试临时堆概念。

## 快速启动

### 1. 准备环境变量

至少需要配置：

```env
DASHSCOPE_API_KEY=your-dashscope-api-key
DD_RAG_JWT_SECRET=replace-with-a-long-random-secret
```

不要把真实密钥提交到 Git。

### 2. 单机 Docker 启动

```bash
docker compose up -d --build
```

访问：

```text
前端：http://localhost:5173
后端：http://localhost:18080
MinIO：http://localhost:9001
Elasticvue：http://localhost:8088
```

### 3. 双机部署

双机部署适合小内存服务器：

- 服务器 A：PostgreSQL、Elasticsearch、MinIO、Elasticvue。
- 服务器 B：Ollama、Backend、Frontend。

参考：

- `deploy/two-node/README.md`
- `docs/DEPLOYMENT_RECORD.md`

## 项目结构

```text
DD_Rag
├─ frontend                  # Vue 3 前端
├─ src/main/java/com/dong/ddrag
│  ├─ auth                   # 认证
│  ├─ user                   # 用户与后台管理
│  ├─ identity               # 当前用户上下文
│  ├─ groupmembership        # 组、成员、权限
│  ├─ document               # 文档中心
│  ├─ ingestion              # 文档 ETL
│  ├─ retrieval              # PgVector / Elasticsearch 检索
│  ├─ qa                     # RAG 问答
│  ├─ assistant              # Agent 助手
│  ├─ storage                # MinIO 存储
│  └─ common                 # 公共能力
├─ src/main/resources
│  ├─ db/migration           # Flyway 迁移
│  ├─ mapper                 # MyBatis XML
│  └─ prompts                # Prompt 模板
├─ docker                    # 自定义镜像
├─ deploy/two-node           # 双机部署
└─ docs                      # 项目文档
```

## 推荐阅读

- `docs/PROJECT_READING_GUIDE.md`：项目代码阅读路线。
- `docs/GITHUB_PUBLISH_GUIDE.md`：GitHub clean 快照发布流程。
- `docs/DEPLOYMENT_RECORD.md`：部署记录。

## 开发提醒

- 不要把真实 API Key、密码、Token 提交到仓库。
- Elasticsearch 必须安装 IK 插件，否则中文分词索引会失败。
- `KB_SEARCH` 应理解为 Assistant Tool，而不是普通硬编码分支。
- Assistant 当前使用短期记忆和会话摘要，不做自动长期记忆写入。
- 普通业务接口必须区分系统级 `ADMIN / USER` 和组内 `OWNER / MEMBER`。

## Star

如果这个项目对你理解 Java AI 工程、Spring AI、Spring AI Alibaba、RAG、Agent 或面试项目准备有帮助，可以点一个 Star。

它的目标不是做一个“能聊天”的 demo，而是展示一个 AI 知识库系统从权限、数据、检索、证据、Agent 到部署的完整工程闭环。
