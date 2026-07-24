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
