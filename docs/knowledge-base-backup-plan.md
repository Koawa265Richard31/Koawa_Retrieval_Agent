# 知识库跨设备备份方案

> 版本：v1（2026-08-10）
> 适用：RAGent 学园偶像大师知识库（KB id=`2084920454895685632`，collection=`gakumas-gamekee-pilot-v3`）
> 目标：任一台设备故障/迁移时，能在另一台设备上完整恢复知识库并继续问答。

---

## 1. 需要备份的内容（盘点）

| 层 | 位置 | 内容 | 体积量级 | 必须备份 |
|---|---|---|---|---|
| PostgreSQL（全库） | 服务器 `RAGENT_DATA_ROOT/postgres`（容器 `ragent-postgres-1`，pgvector 17） | `t_knowledge_base/document/chunk/vector`、`t_user`、`t_intent_node`、`t_query_term_mapping`、`t_sample_question`、`t_conversation_*`、`t_message_*`、`t_rag_trace_*`、`t_ingestion_*` | 中（向量表 2089 行 × 1536 维，约几十~几百 MB） | ✅ |
| RustFS 对象存储 | 服务器 `RAGENT_DATA_ROOT/rustfs`（容器 `ragent-rustfs-1`） | 源文件 bucket `gakumas-gamekee-pilot-v3`（入库的 md/文件）、`gakumas-images`（图片缓存）等 | 图片缓存较大 | ✅ 源文件必备；图片缓存可再生成 |
| 本地语料 | `D:\ragent\output\` | `gamekee-gakumas-semantic-v4`（866 篇）、`gamekee-gakumas-pcard-v1`（140 张 P 卡）、`gakumas-faculty-community`、`gakumas-hif-community`、`gakumas-pcard-index`、`gamekee-titles.json`、`eval/` 报告 | 数百 MB | ✅（再采集/再入库用） |
| 配置与密钥 | `deploy\.env`、`deploy/compose.yaml`、`deploy/application-docker.yaml` | 数据库密码、`ADMIN_PASSWORD`、`SILICONFLOW_API_KEY`、端口等 | KB | ✅ 加密保存 |
| 代码 | git 仓库（gitee + github） | 前端/后端/脚本/文档 | — | ✅（已推送） |

> 说明：`output/` 已被 gitignore，属于“语料资产”，需单独备份；不在 git 里。

---

## 2. 备份策略（推荐）

### 2.1 服务器侧（自动，每日 + 保留 14 天）
在服务器上把 `deploy/backup/kb-backup-remote.sh` 加入 crontab：

```bash
# 每天 03:30 备份
30 3 * * * /opt/ragent/source/deploy/backup/kb-backup-remote.sh >> /opt/ragent-backup/backup.log 2>&1
```

脚本做三件事：
1. `docker exec ragent-postgres-1 pg_dump -U ragent -d ragent -Fc` → 全库 custom 格式 dump（含向量，pgvector 扩展随 dump 携带）；
2. `tar -czf` RustFS 数据目录（源文件 + 图片缓存）；
3. 按天归档到 `/opt/ragent-backup/<YYYYmmdd-HHMMSS>/`，自动清理 14 天前的目录。

体积较大时可加“轻量模式”：只 tar `rustfs/gakumas-gamekee-pilot-v3` 桶（源文件），图片缓存由前端 img-proxy 回源再生成。

### 2.2 拉取到本地/其他设备（手动或计划任务）
在本机（Windows）运行 `deploy/backup/kb-backup-pull.ps1`：

```powershell
pwsh -File deploy/backup/kb-backup-pull.ps1 -RemoteHost jd-ecs -LocalRoot D:\ragent-backups
```

- 用 `scp` 把服务器最新备份目录拉到 `D:\ragent-backups\<date>\`；
- 可选参数 `-MirrorDir`：拉取后自动复制到移动硬盘/局域网 NAS/网盘同步文件夹，实现“第二设备副本”；
- 另一台 Linux/Windows 设备同样跑一次 pull 即可拿到最新备份。

### 2.3 源站语料备份
`output/` 目录建议随备份一并复制（或放到网盘同步夹）。它是再采集/再入库的底料，丢失后只能重新爬 GameKee。

---

## 3. 跨设备恢复（Runbook）

### 3.1 新设备首次部署
```bash
# 1) 装 Docker + 拉代码
git clone git@gitee.com:koawa/koawa.agent.git && cd koawa.agent
# 2) 配置环境变量（用备份里的 .env，或 .env.example 重建并保持同一密码）
cd deploy && cp .env.example .env   # 填入与生产一致的值（POSTGRES_PASSWORD / ADMIN_PASSWORD / SILICONFLOW_API_KEY / RUSTFS_*）
```

### 3.2 恢复 PostgreSQL
```bash
cd deploy
# 首次先初始化 schema（空数据目录会自动执行 schema_pg.sql）
docker compose --env-file .env up -d postgres
# 等待 healthy 后，用备份 dump 覆盖
docker exec -i ragent-postgres-1 pg_restore -U ragent -d ragent --clean --if-exists < /path/to/kb-postgres.dump
```
> 注意：`pg_dump -Fc` 含 `CREATE EXTENSION vector`；pgvector 镜像自带该扩展，恢复无额外依赖。向量为 1536 维，恢复后**无需重新向量化**（前提是 embedding 模型仍为 `Qwen/Qwen3-Embedding-8B`，不要改维度）。

### 3.3 恢复 RustFS（源文件）
```bash
mkdir -p /opt/ragent/data && tar -C /opt/ragent/data -xzf /path/to/rustfs.tar.gz
sudo chown -R 10001:10001 /opt/ragent/data/rustfs
```

### 3.4 恢复本地语料（可选）
把备份中的 `output/` 目录复制到 `D:\ragent\output\`（Windows）或对应路径。

### 3.5 启动并验证
```bash
docker compose --env-file .env up -d
docker compose ps          # app / frontend / postgres / rustfs 等全部 Up
```
验证清单见 §4。

---

## 4. 验证清单（恢复后必须勾选）

- [ ] `docker compose ps` 全部 healthy/Up
- [ ] 登录：`POST /api/koawa-agent/auth/login`（admin + ADMIN_PASSWORD）返回 token
- [ ] 文档数：`GET /knowledge-base/2084920454895685632/docs` 总数为 **871**（866 GameKee + 2 教职员 + 2 H.I.F + 1 P卡速查索引）
- [ ] 向量数：`SELECT count(*) FROM t_knowledge_vector WHERE metadata->>'collection_name'='gakumas-gamekee-pilot-v3'` = 2094（2033 旧 + H.I.F 2 + 索引 5…以实际为准）
- [ ] 问答 smoke（RAG 模式）：
  - 「最新的P卡是哪张？」→ 花海 咲季（H.I.F），实装 2026-07-21
  - 「姫崎莉波（SUGAR FLAVOR）的P卡信息」→ 觉醒前/觉醒后卡面 + 数值/技能
  - 「知识库最后更新时间是什么时候？」→ 有具体时间
- [ ] 图片可加载（前端 img-proxy 能回源缓存）
- [ ] 定时备份任务在跑（服务器 `crontab -l`、备份目录有最近文件）

---

## 5. 可选：仅迁移“知识库本体”的轻量方案

如果只是把知识库搬到另一台机器、不需要历史会话/意图/trace：

```bash
# 只导知识库相关表
docker exec ragent-postgres-1 pg_dump -U ragent -d ragent -Fc   -t t_knowledge_base -t t_knowledge_document -t t_knowledge_chunk   -t t_knowledge_document_chunk_log -t t_knowledge_vector   -f /tmp/kb-only.dump
```
再配合 RustFS 的 `gakumas-gamekee-pilot-v3` 桶即可。恢复后 KB 元数据（source_time、向量）都在，问答立即可用。

---

## 6. 注意与红线

- `deploy/.env` 含明文密钥，备份时必须加密（`zip -P` 或 `gpg -c`），不要明文入库/入 git。
- 切勿 `docker compose down -v`；本部署是 bind mount，数据在 `RAGENT_DATA_ROOT`。
- 向量维度固定 1536，**不要**更换 embedding 模型/维度后直接复用旧向量（需新 collection 重新向量化再切流量）。
- RustFS 图片缓存可丢（img-proxy 回源再生成），但 `gakumas-gamekee-pilot-v3` 源文件与 PG dump 必须成对恢复（doc_id 引用关系）。
- 恢复前先停止 app（`docker compose stop app`），避免写入期间恢复造成不一致。
