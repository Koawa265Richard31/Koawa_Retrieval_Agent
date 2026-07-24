# 2 核 8 GB 单机部署

这套 Compose 面向单机演示和灰度环境，使用 PostgreSQL + pgvector，故意不启动 Milvus、本地 LLM、RocketMQ Dashboard 和额外可观测性组件。

## 1. 准备环境变量

```bash
cd deploy
cp .env.example .env
```

至少修改 `.env` 中的 `SILICONFLOW_API_KEY` 和所有 `change-me` 密码。`.env` 已被 Git 忽略。`admin-bootstrap` 会在 PostgreSQL 初始化后用 `ADMIN_PASSWORD` 覆盖示例数据中的 `admin/admin`。

数据目录建议：

- Windows Docker Desktop：`RAGENT_DATA_ROOT=D:/ragent-runtime-data`
- Linux：先创建 `/opt/ragent/data`，再设置 `RAGENT_DATA_ROOT=/opt/ragent/data`

Linux 上 RustFS 容器使用 uid `10001`；若出现 `permission denied`，执行：

```bash
sudo mkdir -p /opt/ragent/data/{postgres,redis,rustfs,rocketmq/namesrv-logs,rocketmq/broker-store,rocketmq/broker-logs}
sudo chown -R 10001:10001 /opt/ragent/data/rustfs
```

PostgreSQL、Redis 和 RocketMQ 的目录权限应按各自容器日志提示调整，不要直接对整个数据根目录使用 `chmod 777`。

## 2. 校验和启动

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env build app mcp-server
docker compose --env-file .env up -d
docker compose ps
docker compose logs -f app
```

首次创建 PostgreSQL 数据目录时会自动执行 `schema_pg.sql` 和 `init_data_pg.sql`。已有数据目录不会重复执行；数据库结构升级应使用独立迁移脚本，不能靠删除数据卷重建。

从 v1.2 升级到包含 Agentic Retrieval Trace 的版本时，先执行：

```bash
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -f /dev/stdin < ../resources/database/upgrade_v1.2_to_v1.3.sql
```

应用地址：`http://<server-ip>:9090/api/koawa-agent`。

## 3. 资源边界

- Java 应用最大堆：1536 MB；Hikari 最大连接数：5。
- Redis 最大数据内存：192 MB。
- 文档上传并发：1；聊天并发：2。
- RocketMQ 使用单 NameServer、单 Broker，不启动 Proxy 和 Dashboard。
- Agent 默认关闭、灰度比例为 0；先完成 RAG smoke test，再调到 1%～5%。

## 4. 硅基流动模型

当前部署配置使用：

- Chat：`deepseek-ai/DeepSeek-V3.2`
- Embedding：`Qwen/Qwen3-Embedding-8B`，固定输出 1536 维
- Rerank：暂时关闭

当前项目已有硅基流动 Chat/Embedding 客户端，但 Rerank 只有百炼与 noop 客户端。虽然配置中保留了 `/v1/rerank` 端点，在实现并测试 `SiliconFlowRerankClient` 前不要开启 `rag.rerank.enabled`。

切换 Embedding 模型或维度会使已有向量不可比较。上线后不要直接修改 `rag.default.dimension`；应新建 collection、重新向量化，再切流量。

## 5. 停止与更新

```bash
docker compose down
docker compose build app mcp-server
docker compose up -d
```

不要使用 `docker compose down -v`：本配置使用 bind mount，数据仍在 `RAGENT_DATA_ROOT`，但该命令容易形成错误的“数据已清理”预期。
