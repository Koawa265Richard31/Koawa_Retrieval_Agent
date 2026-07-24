# Agentic Retrieval AR3：复杂度路由与小流量启用

## 1. 阶段结果

AR3 将 AR2 的有界检索循环接入主流水线，但默认仍为 `OFF`。系统现在支持：

- 规则版复杂度判定；
- conversationId 优先、userId 兜底的稳定分桶；
- `OFF`、`SHADOW`、`ACTIVE` 三种运行模式；
- Active 失败时 100% 回退首轮 `RetrievalContext`；
- `/rag/settings` 展示当前模式、灰度比例、超时和最大轮次；
- 离线路由混淆矩阵。

## 2. 请求决策

```text
首轮 RetrievalEngine
  -> RetrievalComplexityDecider
  -> 稳定会话分桶
  -> OFF:    使用首轮结果
  -> SHADOW: 后台运行 AR2，回答仍使用首轮结果
  -> ACTIVE: 同步运行 AR2，成功则使用增强结果
                |
                +-> 超时/取消/规划/检索/评估失败/异常
                    回退首轮结果
```

首版规则刻意保守：

- 改写结果包含两个及以上子问题时判为复杂；
- 或意图解析得到两个及以上子意图时判为复杂；
- 单问题不会仅因文本长或包含关键词而进入 Agentic Retrieval。

保守规则优先控制简单问题的额外模型调用和延迟。后续只能根据真实 Trace
回放数据调规则，不能凭直觉增加模糊关键词。

## 3. 配置

```yaml
rag:
  agentic-retrieval:
    mode: off
    rollout-percentage: 0
```

Docker 环境变量：

```text
RAG_AGENTIC_RETRIEVAL_MODE=off|shadow|active
RAG_AGENTIC_RETRIEVAL_ROLLOUT_PERCENTAGE=0..100
```

旧的 `RAG_AGENTIC_RETRIEVAL_SHADOW_ENABLED=true` 仍映射为 `SHADOW`，仅用于
兼容 AR1/AR2 部署；新部署不再依赖它。

稳定分桶使用 conversationId，缺失时才使用 userId。因而同一对话不会在多轮中
反复切换模式，不同对话仍可分别进入灰度。

## 4. 离线矩阵

运行：

```powershell
python scripts/eval/run_agentic_retrieval_complexity_matrix.py `
  --cases resources/eval/agentic-retrieval/v1/complexity-cases.json `
  --output resources/eval/agentic-retrieval/v1/complexity-matrix.json
```

首版开发集共 20 条：

| 指标 | 结果 |
|---|---:|
| 简单正确 | 10 |
| 简单误判复杂 | 0 |
| 复杂漏判简单 | 0 |
| 复杂正确 | 10 |
| 简单误路由率 | 0% |

该数据是规则开发集，只证明实现与当前标注契合，不代表真实生产质量。AR3 进入
真实流量前仍需从 Trace 抽取盲测集，复验 Recall、P95、额外模型调用和 ACL。

## 5. 验证与灰度

自动化覆盖：

- 简单问题不进入 Agentic Retrieval；
- 相同 conversationId 的分桶稳定；
- 旧 Shadow 配置兼容；
- Shadow 不替换回答上下文；
- Active 成功使用增强上下文；
- Active StopReason 失败及异常均回退首轮上下文。

推荐启用顺序：

```text
本地 OFF
  -> 测试环境 ACTIVE 100%
  -> 线上 SHADOW 100%
  -> 内部 ACTIVE 5%
  -> 20% -> 50%
```

只有真实回放集满足规划书中的全部门槛后才提升比例；`mode=off` 是即时回滚开关。
