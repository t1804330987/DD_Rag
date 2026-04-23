# DD_Rag 项目阅读指南

> 这份文档用于快速理解当前代码实现。当前项目已经从早期 demo 收敛为“认证 + 组织知识库 + 文档入库 + 混合检索问答 + 个人助手 + 管理后台 + Docker 部署”的完整链路。

## 1. 先读哪几份文档

建议按这个顺序读：

1. `docs/PROJECT_READING_GUIDE.md`
   先建立当前代码地图和阅读路线。
2. `docs/DEBUG_NOTES.md`
   看已经踩过的坑、真实根因和固定处理方式。
3. `docs/DEPLOYMENT_RECORD.md`
   看单机部署、双机部署、端口、镜像和服务器安装记录。
4. `docs/VERSION_HISTORY.md`
   看版本演进脉络。
5. `docs/DD_Rag MVP V1.md`
   了解最早的 MVP 主链如何形成。
6. `docs/AI_COLLABORATIVE_DEVELOPMENT_SOP.md`
   了解本项目 AI 协作开发流程。

补充资料：

- `docs/V2/`：组角色、邀请制、按组文件管理。
- `docs/V4/`：自助注册、组织 ID 申请加入、OWNER 审批。
- `docs/V5/`：RAG 检索增强、查询规划、混合召回。
- `docs/V5.1/`：助手、短期记忆、ReAct Agent、历史设计记录。
- `docs/superpowers/plans/`：阶段性实施计划和收口过程。

注意：`docs/V5.1/*LONG_TERM_MEMORY*` 属于历史设计文档。当前代码已经删除长期记忆主链，不应把它当作现行实现依据。

## 2. 当前项目整体理解

当前系统可以按 7 个稳定板块理解：

1. 认证与系统用户
2. 组、成员与权限
3. 文档中心与入库
4. 混合检索问答
5. 个人智能助手
6. 管理后台
7. Docker 部署与服务器运行

当前主链：

```text
用户注册 / 登录
-> USER 进入普通业务区
-> 创建组或加入组
-> OWNER 上传文档
-> 文档写入 MinIO
-> 同步解析 / 清洗 / 切片 / chunk 落库 / 向量写入 / ES 索引
-> OWNER 或 MEMBER 在组内进行 QA
-> QA 走查询规划 + PgVector + Elasticsearch + RRF 融合
-> 模型基于证据回答或拒答
-> 用户也可以进入 Assistant 做连续对话
-> Assistant 的 KB_SEARCH 通过工具调用复用 QA 检索能力
```

系统级角色与组内角色必须分开理解：

- `ADMIN / USER` 是系统级角色。
- `OWNER / MEMBER` 是组内角色。
- `ADMIN` 只进入后台管理区，不自动拥有普通业务区权限。
- `USER` 才进入组、文档、QA、助手等普通业务区。

## 3. 前端页面地图

前端入口：

- `frontend/src/main.ts`
- `frontend/src/App.vue`
- `frontend/src/router/index.ts`

当前路由：

- `/login`：登录页。
- `/register`：注册页。
- `/account/security`：账户安全页，主要承接首次改密。
- `/groups`：协作小组 / 组织知识库工作台。
- `/documents`：文档中心。
- `/qa`：智能检索问答。
- `/assistant`：个人智能助手。
- `/admin/overview`：管理后台概览。
- `/admin/users`：用户管理列表。
- `/admin/users/:userId`：用户详情。

共享布局：

- `frontend/src/components/layout/WorkbenchShell.vue`
- `frontend/src/components/layout/WorkbenchSidebar.vue`
- `frontend/src/components/layout/PageHeaderHero.vue`
- `frontend/src/assets/workbench-shell.css`
- `frontend/src/assets/page-shell.css`

页面样式：

- `frontend/src/assets/login-page.css`
- `frontend/src/assets/account-security-page.css`
- `frontend/src/assets/groups-page.css`
- `frontend/src/assets/document-page.css`
- `frontend/src/assets/qa-page.css`
- `frontend/src/assets/assistant-page.css`
- `frontend/src/assets/admin-page.css`

状态与 API：

- `frontend/src/stores/auth.ts`：登录态、当前用户、角色跳转。
- `frontend/src/stores/app.ts`：业务工作台状态。
- `frontend/src/api/http.ts`：HTTP 客户端和认证请求处理。
- `frontend/src/api/auth.ts`：认证接口。
- `frontend/src/api/group.ts`：组、成员、申请、邀请接口。
- `frontend/src/api/document.ts`：文档接口。
- `frontend/src/api/qa.ts`：QA 接口。
- `frontend/src/api/assistant.ts`：助手接口。
- `frontend/src/api/admin-user.ts`：后台用户管理接口。

阅读前端时不要从样式文件开始。先看路由、store、page，再看 page-owned components，最后看 CSS。

## 4. 后端模块地图

主包目录：

```text
src/main/java/com/dong/ddrag
├─ common
├─ auth
├─ user
├─ identity
├─ groupmembership
├─ document
├─ ingestion
├─ retrieval
├─ qa
├─ assistant
└─ storage
```

模块职责：

- `common`：统一返回体、异常、枚举。
- `auth`：登录、注册、JWT、refresh token、认证 Cookie、dev admin 初始化。
- `user`：账户安全、后台用户管理、用户查询。
- `identity`：当前登录用户解析与业务用户 / 管理员边界校验。
- `groupmembership`：组、成员、OWNER / MEMBER 权限、邀请、加入申请。
- `document`：文档元数据、上传、列表、预览、删除。
- `ingestion`：文档解析、清洗、切片、chunk 落库、向量写入。
- `retrieval`：PgVector 语义召回、Elasticsearch 关键词检索。
- `qa`：查询规划、混合召回、证据组织、模型回答、引用组装。
- `assistant`：会话、多轮消息、短期记忆、ReAct Agent、KB 检索工具。
- `storage`：MinIO 对象存储抽象。

如果只抓主线，优先看：

```text
auth -> identity -> groupmembership -> document -> ingestion -> qa -> assistant
```

## 5. 推荐阅读顺序

### 第一步：启动与配置

先读：

- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/java/com/dong/ddrag/DDRagApplication.java`
- `docker-compose.yml`

重点看：

- Java / Spring Boot / Spring AI / Spring AI Alibaba 版本。
- PostgreSQL、pgvector、Elasticsearch、MinIO、Ollama、DashScope 的配置来源。
- `DASHSCOPE_API_KEY` 当前通过环境变量注入，不应硬编码。

### 第二步：认证和当前用户

建议顺序：

1. `auth/controller/AuthController.java`
2. `auth/service/AuthService.java`
3. `auth/security/JwtAuthenticationFilter.java`
4. `auth/security/JwtAccessTokenService.java`
5. `auth/security/RefreshTokenService.java`
6. `identity/service/CurrentUserService.java`
7. `user/controller/AccountController.java`
8. `user/service/AccountService.java`

要点：

- access token 由前端内存态持有。
- refresh token 通过 HttpOnly Cookie 与数据库记录维护。
- 退出登录、改密会撤销 refresh token。
- 普通业务入口应使用 `requireBusinessUser(...)`。
- 后台接口应使用 `requireSystemAdmin(...)`。

### 第三步：组和权限

建议顺序：

1. `groupmembership/controller/GroupQueryController.java`
2. `groupmembership/controller/GroupManagementController.java`
3. `groupmembership/controller/GroupJoinRequestController.java`
4. `groupmembership/controller/InvitationDecisionController.java`
5. `groupmembership/service/GroupMembershipService.java`
6. `groupmembership/service/GroupManagementService.java`
7. `groupmembership/service/GroupJoinRequestService.java`

要点：

- `OWNER` 管理组、成员、文档。
- `MEMBER` 读取组内知识库并参与问答。
- 加入方式包括 OWNER 邀请和用户按组织 ID 申请。
- 组内权限是文档与 QA 的前置条件。

### 第四步：文档入库

建议顺序：

1. `document/controller/DocumentController.java`
2. `document/service/DocumentService.java`
3. `storage/service/MinioStorageService.java`
4. `ingestion/service/EtlDocumentIngestionProcessor.java`
5. `ingestion/reader/StoredObjectDocumentReader.java`
6. `ingestion/parser/factory/DocumentParserFactory.java`
7. `ingestion/parser/strategy/*`
8. `ingestion/transformer/TextCleanupTransformer.java`
9. `ingestion/transformer/StructureAwareChunkTransformer.java`
10. `ingestion/chunk/ChunkService.java`
11. `ingestion/vector/VectorIngestionService.java`
12. `retrieval/elasticsearch/ElasticsearchChunkIndexService.java`

当前入库链路：

```text
上传文件
-> DocumentService 校验组权限
-> 文件写入 MinIO
-> 创建 document / ingestion job
-> StoredObjectDocumentReader 读取对象
-> DocumentParserFactory 选择解析器
-> TextCleanupTransformer 清洗文本
-> StructureAwareChunkTransformer 切片
-> ChunkService 写 document_chunks
-> VectorIngestionService 写 pgvector
-> ElasticsearchChunkIndexService 写 ES 索引
```

要点：

- 当前是同步 ETL。
- 文档可问答的前提是 `READY` 且未删除。
- ES 需要 IK 分词器，否则会出现 `ik_max_word` tokenizer 找不到的 400 错误。

### 第五步：QA 检索问答

建议顺序：

1. `qa/controller/QaController.java`
2. `qa/service/QaService.java`
3. `qa/service/QueryPlanningService.java`
4. `qa/rag/ReadyChunkDocumentRetriever.java`
5. `qa/rag/HybridChunkRetrievalService.java`
6. `retrieval/vectorstore/PgVectorRetrievalAdapter.java`
7. `retrieval/elasticsearch/ElasticsearchChunkIndexService.java`
8. `qa/service/QaChatService.java`
9. `qa/support/QaAnswerParser.java`
10. `qa/support/CitationAssembler.java`

当前 QA 主链：

```text
用户问题 + groupId
-> QaService 校验组可读权限
-> QueryPlanningService 生成 DIRECT / REWRITE / DECOMPOSE
-> ReadyChunkDocumentRetriever 作为 retriever 门面
-> HybridChunkRetrievalService 执行混合召回
-> PgVectorRetrievalAdapter 语义召回
-> ElasticsearchChunkIndexService 关键词召回
-> RRF 融合候选
-> 聚簇和扩窗生成 evidence bundle
-> QaChatService 约束模型基于证据回答
-> QaAnswerParser 解析结构化输出
-> CitationAssembler 组装引用
```

要点：

- 查询规划失败时 fallback 为原问题直查。
- 模板渲染使用 Spring AI `PromptTemplate` 时，注意 `{变量}` 与模板内 JSON / 花括号冲突问题。
- 无证据时应拒答，而不是让模型自由发挥。
- 当前 `citations` 更接近“参考文件 / 证据列表”，不是完整论文式引用系统。

### 第六步：个人智能助手

建议顺序：

1. `assistant/controller/AssistantSessionController.java`
2. `assistant/controller/AssistantConversationController.java`
3. `assistant/controller/AssistantChatController.java`
4. `assistant/service/AssistantSessionService.java`
5. `assistant/service/AssistantConversationService.java`
6. `assistant/service/AssistantService.java`
7. `assistant/agent/AssistantAgentFacade.java`
8. `assistant/agent/AssistantReactAgentFactory.java`
9. `assistant/agent/AssistantKnowledgeBaseTool.java`
10. `assistant/memory/AssistantShortTermMemoryHook.java`
11. `assistant/memory/AssistantShortTermMemoryMaintenanceService.java`
12. `assistant/memory/AssistantSessionSummaryService.java`

当前助手主链：

```text
用户进入 /assistant
-> 新建或选择会话
-> 前端发送 streamChat
-> AssistantService 保存用户消息
-> 触发短期记忆维护
-> AssistantAgentFacade 调用 ReactAgent
-> AssistantShortTermMemoryHook 在 beforeModel 重组上下文
-> CHAT 模式直接回答
-> KB_SEARCH 模式由模型调用 AssistantKnowledgeBaseTool
-> Tool 内部按 groupId 调用知识库检索
-> 工具结果作为观察重新进入模型
-> 模型生成最终回答
-> AssistantService 保存助手消息和引用
```

要点：

- 当前没有长期记忆表和自动长期记忆写入主链。
- `V12__drop_assistant_long_term_memory_tables.sql` 已删除长期记忆相关表。
- `AssistantShortTermMemoryHook` 是模型调用前重组上下文的关键点。
- 工具调用后的上下文必须保留工具观察，否则模型可能重复调用工具。
- `recursionLimit` 应限制工具最大推理轮数，避免工具循环调用。
- `KB_SEARCH` 本质上是 Assistant 的一个 Tool，不是 Service 中硬编码的单独分支。

### 第七步：管理后台

建议顺序：

1. `frontend/src/pages/admin/AdminLayout.vue`
2. `frontend/src/pages/admin/AdminOverviewPage.vue`
3. `frontend/src/pages/admin/users/AdminUsersPage.vue`
4. `frontend/src/pages/admin/users/AdminUserDetailPage.vue`
5. `user/controller/AdminUserController.java`
6. `user/service/AdminUserService.java`
7. `user/service/UserQueryService.java`

当前定位：

- 后台用于系统用户查看、状态管理、密码重置和详情查看。
- 前端不提供创建用户入口。
- 普通用户注册走 `/register`。
- 管理后台不进入普通业务空间。

## 6. 数据库与迁移

迁移文件位置：

```text
src/main/resources/db/migration
```

重点迁移：

- `V1__init_core_tables.sql`：核心表初始化。
- `V7__add_auth_productization.sql`：认证产品化。
- `V8__add_group_join_requests.sql`：加入申请。
- `V9__create_assistant_tables.sql`：助手会话与消息。
- `V12__drop_assistant_long_term_memory_tables.sql`：删除助手长期记忆表。

阅读数据库时建议和 mapper 一起看：

- `src/main/resources/mapper/groupmembership/*`
- `src/main/resources/mapper/document/*`
- `src/main/resources/mapper/ingestion/*`
- `src/main/resources/mapper/assistant/*`

## 7. Docker 与部署

单机部署入口：

- `docker-compose.yml`
- `docker/elasticsearch/Dockerfile`

双机部署入口：

- `deploy/two-node/docker-compose.server-a.yml`
- `deploy/two-node/docker-compose.server-b.yml`
- `deploy/two-node/server-a.env.example`
- `deploy/two-node/server-b.env.example`
- `deploy/two-node/README.md`

部署记录：

- `docs/DEPLOYMENT_RECORD.md`

当前部署理解：

- Server A 更适合放基础设施：PostgreSQL、Elasticsearch、MinIO、Elasticvue。
- Server B 更适合放业务服务：Ollama、backend、frontend。
- Server A 需要给 Server B 放通 PostgreSQL、ES、MinIO 端口。
- Server B 需要对外放通前端和后端端口。
- ES 镜像必须包含 IK 插件，项目提供 `docker/elasticsearch/Dockerfile`。
- Ollama 模型下载较慢时，可以单独处理模型，不建议每次 compose build 都重新拉取。

发布 GitHub 时当前采用 clean snapshot 策略：

- 原始仓库：`E:\AICode\DD_Rag`
- 干净发布仓库：`E:\AICode\DD_Rag_clean`
- 目标远程：`https://github.com/t1804330987/DD_Rag`

原因：

- 原始 Git 历史曾出现敏感 key。
- clean 仓库只保留当前快照，不携带旧提交历史。
- 推送前必须做敏感信息扫描。

## 8. 按问题导向阅读

### 我想知道登录后用户是谁

看：

- `frontend/src/stores/auth.ts`
- `auth/security/JwtAuthenticationFilter.java`
- `identity/service/CurrentUserService.java`

### 我想知道 ADMIN 为什么不能进业务区

看：

- `frontend/src/router/index.ts`
- `frontend/src/stores/auth.ts`
- `identity/service/CurrentUserService.java`
- `user/controller/AdminUserController.java`
- `groupmembership/service/GroupMembershipService.java`

### 我想知道组织知识库怎么选

看：

- `frontend/src/stores/app.ts`
- `frontend/src/components/GroupSelector.vue`
- `frontend/src/pages/group/GroupsPage.vue`
- `groupmembership/controller/GroupQueryController.java`

### 我想知道上传文件后为什么能检索

看：

- `DocumentService`
- `EtlDocumentIngestionProcessor`
- `ChunkService`
- `VectorIngestionService`
- `ElasticsearchChunkIndexService`

### 我想知道为什么不会串组

看：

- `GroupMembershipService`
- `QaService`
- `ReadyChunkDocumentRetriever`
- `HybridChunkRetrievalService`
- `PgVectorRetrievalAdapter`
- `ElasticsearchChunkIndexService`

重点确认 `groupId` 是否从入口一路传到检索过滤条件。

### 我想知道知识库对话为什么会走 QueryPlanningService

看：

- `AssistantKnowledgeBaseTool`
- `QaService`
- `QueryPlanningService`
- `HybridChunkRetrievalService`

原因：

```text
Assistant KB_SEARCH
-> Tool 调用 QA 检索
-> QA 检索前统一走 QueryPlanningService
```

这不是异常路径，而是当前知识库检索的标准链路。

### 我想知道 Assistant 的上下文怎么送进模型

看：

- `AssistantService`
- `AssistantAgentFacade`
- `AssistantReactAgentFactory`
- `AssistantShortTermMemoryHook`
- `AssistantShortTermMemoryMaintenanceService`

重点看：

- 用户消息先落库。
- 短期记忆维护会更新会话摘要。
- `beforeModel(...)` 会把摘要、历史消息、工具观察和当前问题重组为模型输入。
- 工具观察必须进入上下文，否则模型可能不知道工具已经执行过。

### 我想知道会话删除 / 重命名 / 自动命名

看：

- `AssistantSessionController`
- `AssistantSessionService`
- `AssistantSessionMapper`
- `frontend/src/components/assistant/AssistantSessionColumn.vue`
- `frontend/src/api/assistant.ts`

当前目标是会话硬删除时同时删除消息和上下文。

### 我想知道前端四个主业务页为什么要统一样式

看：

- `/groups`：`GroupsPage.vue`
- `/documents`：`DocumentPage.vue`
- `/qa`：`QaPage.vue`
- `/assistant`：`AssistantPage.vue`
- 共享 CSS：`workbench-shell.css`、`page-shell.css`

当前约束：

- 主内容卡片圆角、边框、padding、标题层级需要统一。
- 图片和页面专属视觉资产不要随意改。
- 布局统一优先于单页自由发挥。

## 9. 当前最容易误读的点

### 1. KB_SEARCH 不是旧的硬编码模式

当前更合理的理解是：

```text
KB_SEARCH = Assistant 可调用的知识库检索 Tool
```

前端仍需要保留知识库选择，因为 Tool 调用需要 `groupId` 等上下文。

### 2. Assistant 没有长期记忆自动写入

当前应按短期记忆和会话摘要理解：

- 维护连续对话上下文。
- 控制长会话输入长度。
- 不让模型自动乱写长期偏好。

### 3. QueryPlanningService 是 QA 的前置层

它不是 Assistant 特有逻辑。只要走知识库检索主链，就可能进入 QueryPlanningService。

### 4. ES 报 IK tokenizer 错误是部署问题

如果出现：

```text
Custom Analyzer [ddrag_ik_index] failed to find tokenizer under name [ik_max_word]
```

优先检查 ES 容器是否使用了项目自定义镜像，以及 IK 插件是否安装成功。

### 5. 前端网络路径要区分 dev proxy 和后端真实 API

本地 Vite 可通过代理访问接口；服务器部署时要看 `VITE_API_BASE_URL`、后端 context path、浏览器实际请求 URL 是否一致。不要只看容器内环境变量就判断链路正确。

## 10. 最短阅读路线

如果只有 30 分钟，按这个顺序读：

1. `frontend/src/router/index.ts`
2. `frontend/src/stores/auth.ts`
3. `frontend/src/pages/group/GroupsPage.vue`
4. `frontend/src/pages/document/DocumentPage.vue`
5. `frontend/src/pages/qa/QaPage.vue`
6. `frontend/src/pages/assistant/AssistantPage.vue`
7. `src/main/java/com/dong/ddrag/identity/service/CurrentUserService.java`
8. `src/main/java/com/dong/ddrag/groupmembership/service/GroupMembershipService.java`
9. `src/main/java/com/dong/ddrag/document/service/DocumentService.java`
10. `src/main/java/com/dong/ddrag/ingestion/service/EtlDocumentIngestionProcessor.java`
11. `src/main/java/com/dong/ddrag/qa/service/QaService.java`
12. `src/main/java/com/dong/ddrag/qa/rag/HybridChunkRetrievalService.java`
13. `src/main/java/com/dong/ddrag/assistant/service/AssistantService.java`
14. `src/main/java/com/dong/ddrag/assistant/memory/AssistantShortTermMemoryHook.java`
15. `src/main/java/com/dong/ddrag/assistant/agent/AssistantKnowledgeBaseTool.java`
16. `docs/DEPLOYMENT_RECORD.md`

## 11. 改代码前的建议

改后端前先确认：

- 当前入口属于普通业务区还是后台管理区。
- 是否需要 `requireBusinessUser(...)` 或 `requireSystemAdmin(...)`。
- 是否需要组内 `OWNER / MEMBER` 权限。
- 是否会影响文档入库、QA 检索或 Assistant 工具链。
- 是否需要新增 Flyway 迁移。

改前端前先确认：

- 是否属于四个主业务页的统一布局范围。
- 是否需要同步调整 page-owned component。
- 是否改变路由权限。
- 是否影响移动端和桌面端布局。
- 是否会破坏当前页面已统一的主内容卡片风格。

改部署前先确认：

- 是单机部署还是双机部署。
- 是否会影响端口暴露。
- 是否会把密钥写进仓库。
- ES 是否仍使用带 IK 的镜像。
- clean 仓库是否需要同步同一份文件。
