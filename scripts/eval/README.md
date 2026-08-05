# RAG 离线评测脚本

## Agentic Retrieval AR0 基线

评测数据：

```text
resources/eval/agentic-retrieval/v1/cases.json
```

数据集使用仓库内 `resources/docs/knowledge` 的企业知识文档，包含：

- 跨文档组合问题；
- 单文档多章节问题；
- 多实体比较问题；
- 多跳推理问题；
- 知识库无法回答的问题。

只校验数据集：

```powershell
python scripts/eval/run_agentic_retrieval_baseline.py --validate-only
```

Start an isolated deployment with `APP_EVAL_ENABLED=true`, then prepare the
versioned document fixture through the application's public APIs:

```powershell
python scripts/eval/prepare_agentic_retrieval_fixture.py
```

The baseline runner logs in using the untracked `deploy/.env` file. It never
prints or stores the resulting token:

```powershell
python scripts/eval/run_agentic_retrieval_baseline.py
```

Keep `APP_EVAL_ENABLED=false` for normal and production deployments.

执行基线：

```powershell
$env:RAGENT_EVAL_TOKEN = "<登录后获得的 Token>"
python scripts/eval/run_agentic_retrieval_baseline.py `
  --base-url http://127.0.0.1:9090/api/koawa-agent
```

应用必须显式启用：

```yaml
app:
  eval:
    enabled: true
```

生产环境默认保持关闭。不要为了运行评测而公开生产评测接口。

输出默认写入：

```text
output/eval/agentic-retrieval/baseline.json
output/eval/agentic-retrieval/baseline.md
```

当前脚本计算：

- Recall@5；
- MRR@5；
- 全部目标文档命中率；
- 无答案问题空召回率；
- 平均和 P95 检索延迟；
- 平均拆分子问题数量。

AR0 不评估最终答案文本。`required_facts` 的语义证据覆盖将在 AR1 的
`EvidenceEvaluator` 中实现。

## 学园偶像大师 Single Pass / Agentic 对比

先通过 fixture 脚本幂等导入演示知识库，再启动启用了评测入口的新版本应用：

```powershell
python scripts/eval/prepare_agentic_retrieval_fixture.py `
  --dataset resources/eval/agentic-retrieval/v1/gakumas-fixture.json `
  --knowledge-base-name gakumas-agentic-demo-v1 `
  --collection-name gakumas-agentic-demo-v1

python scripts/eval/run_gakumas_agentic_comparison.py
```

脚本从 `resources/eval/agentic-retrieval/v1/gakumas-questions.json` 读取固定的
5 条问题，并对每条问题分别调用：

```text
GET /rag/eval?question=<问题>&mode=single
GET /rag/eval?question=<问题>&mode=active
```

默认报告写入：

```text
resources/eval/agentic-retrieval/v1/gakumas-comparison-summary.json
```

该报告比较来源召回、延迟、Agentic 回退、复杂问题路由和引用目录一致性。
它不生成最终答案，因此不能替代事实句引用正确率验收；脚本使用管理员账号，
ACL 用例也只保留在数据集中，普通用户拒绝访问必须另行回归。

## RAG / Agent Loop 最终回答对照

用于比较对外聊天模式 `RAG` 与完整 `AGENT` loop 的最终回答质量：

```powershell
python scripts/eval/run_chat_mode_comparison.py --validate-only

python scripts/eval/run_chat_mode_comparison.py `
  --base-url http://127.0.0.1:9090/api/koawa-agent
```

默认测试集：

```text
resources/eval/chat-mode-comparison/v1/gakumas-chat-cases.json
```

默认报告：

```text
resources/eval/chat-mode-comparison/v1/latest-summary.json
```

该脚本直接调用 SSE 聊天入口 `/rag/v3/chat`，分别传：

```text
executionMode=RAG
executionMode=AGENT
```

每条用例会检查：

- 是否收到 `[DONE]`；
- 是否要求澄清；
- 必含角色/事实词是否出现；
- 禁止编造词是否出现；
- Markdown 图片数量是否达到预期；
- 两种模式的延迟差异；
- Agent 相对 RAG 的退化用例和胜出用例。
