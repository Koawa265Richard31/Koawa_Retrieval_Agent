# Agentic Retrieval AR0 基线报告

## 1. 执行结论

AR0 已在不改变生产检索行为的前提下完成。固定数据集包含 20 条复杂问题和
5 条简单对照问题，9 份版本化 Markdown 文档均通过应用公开接口完成上传、
异步分块、向量化和 PGVector 持久化。

执行环境：

- 日期：2026-07-24；
- 数据集：`ragent-agentic-retrieval-v1`；
- 应用：本地 Docker Compose 隔离栈；
- 评测入口：仅绑定 `127.0.0.1`，`app.eval.enabled=true`；
- 向量模型：`Qwen/Qwen3-Embedding-8B`；
- Chat 模型：`deepseek-ai/DeepSeek-V3.2`；
- 检索实现：现有 `QueryRewriteService -> IntentResolver -> RetrievalEngine`；
- Agentic Retrieval：未启用。

## 2. 指标

| 指标 | 全部 | simple | agentic |
| --- | ---: | ---: | ---: |
| 用例数 | 25 | 5 | 20 |
| 执行成功率 | 100% | 100% | 100% |
| Recall@5 | 100% | 100% | 100% |
| MRR@5 | 97.73% | 90% | 100% |
| 目标文档全部命中率 | 100% | 100% | 100% |
| 无答案问题空召回率 | 0% | N/A | 0% |
| 平均延迟 | 10,454.24 ms | 8,987.40 ms | 10,820.95 ms |
| P95 延迟 | 18,415 ms | — | — |
| 平均子问题数 | 1.12 | 1.00 | 1.15 |

当前评测入口不生成最终答案，因此“引用命中率”以目标文档全部命中率作为
检索阶段代理指标。实际答案引用正确率在 AR4 的结构化引用阶段测量。

当前模型客户端没有向上暴露 usage，无法可靠记录实际 Token；评测 Trace 也
没有请求级模型调用计数。两项均记为 `N/A`，由 AR1 的 Trace 与指标扩展补齐，
不得用估算值冒充供应商实际 usage。

## 3. 失败样例分类

虽然 25 条请求全部执行成功，但存在三类效果失败：

1. **无答案问题过度召回**：3/3 无答案问题仍返回文档，空召回率为 0%。
   当前链路没有证据充分性判断，容易把“制度规范”误当成“实时事实或版本通知”。
2. **复杂问题拆分不足**：20 条复杂问题平均仅 1.15 个子问题，明显低于数据集
   中标注的多跳需求；这直接构成 AR1 EvidenceLedger 与 AR2 补检索的输入依据。
3. **文档级指标饱和**：候选集只有 9 份文档，检索结果经常覆盖大部分文档，
   因而 Recall@5 为 100% 但不能证明必需事实已经覆盖。AR1 必须下沉到
   `required_facts` 和 Chunk 级证据充分性，不能继续只看文档 Recall。

## 4. 可复现命令

```powershell
$env:APP_EVAL_ENABLED = "true"
$env:APP_BIND_ADDRESS = "127.0.0.1"
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build
python scripts/eval/prepare_agentic_retrieval_fixture.py
python -m unittest scripts.eval.test_run_agentic_retrieval_baseline
python scripts/eval/run_agentic_retrieval_baseline.py
```

运行时完整明细写入 `output/eval/agentic-retrieval/`，该目录不进入版本控制；
用于版本间比较的稳定摘要保存在数据集目录。

## 5. AR0 验收

- [x] 至少 20 条复杂问题，并包含多实体、跨文档、多跳和无答案场景；
- [x] 每条记录期望子问题、目标文档、必需事实和是否拒答；
- [x] 固定版本可重复执行；
- [x] simple 与 agentic 分组统计；
- [x] 指标自动计算，不依赖逐条人工阅读；
- [x] 基线执行脚本、报告和失败分类已固化；
- [x] 不改变生产检索行为，评测端点默认关闭。

AR0 阶段门通过，下一阶段只能进入 AR1 Shadow Evidence Evaluation，不得直接
开启第二轮检索。
