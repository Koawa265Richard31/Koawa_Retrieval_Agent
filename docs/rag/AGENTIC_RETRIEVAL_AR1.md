# Agentic Retrieval AR1 实现与验收记录

## 1. 阶段结论

AR1 已建立独立于当前回答路径的证据账本、确定性检查和 LLM 证据评估能力。
该能力默认关闭；开启 Shadow 后只读取当前检索结果并异步评估，不替换
`RetrievalContext`，不参与最终回答。

## 2. 已实现内容

- 领域模型：`RetrievalBudget`、`RetrievalTask`、`RetrievalPlan`、
  `EvidenceItem`、`EvidenceLedger`、`EvidenceEvaluation`、
  `AgenticRetrievalResult` 及停止原因、缺口和任务状态模型。
- `RetrievalContextEvidenceAdapter`：把现有按意图分组的 Chunk 转换为
  带任务归属的证据。
- `DeterministicEvidenceChecks`：空证据、任务覆盖和证据状态的快速判断。
- `LlmEvidenceEvaluator`：严格 JSON 输出、输入条数和字符数限制、8 秒调用期限。
- `EvidenceEvaluationParser`：拒绝非法 JSON、未知或重复任务、任务缺失和越界置信度。
- `AgenticRetrievalShadowService`：有界异步队列、默认关闭、拒绝和执行异常隔离。
- `EVIDENCE_EVALUATION` Trace 节点，以及包含逐任务状态、证据数、缺口数和
  置信度的结构化日志。
- PostgreSQL Trace 节点类型字段扩展到 32 字符，并提供
  `upgrade_v1.2_to_v1.3.sql` 升级脚本。

## 3. 安全边界

- Shadow 不返回新的检索上下文，现有回答生成逻辑不读取评估结果。
- 评估超时、模型错误、非法输出和队列满都不会中断用户请求。
- Prompt 最多携带 20 条证据，每条最多 1200 字符。
- 日志不打印证据正文、Token 或模型密钥。

## 4. 自动化验收

聚焦测试共 18 条，覆盖：

- Evidence 合并与去重；
- 空证据、部分覆盖和冲突；
- RetrievalContext 适配；
- 合法与非法 Evaluator JSON；
- LLM 超时；
- Shadow 关闭、开启和执行失败隔离。

全量 Maven 测试在阶段提交前执行。

## 5. Docker 真实链路验收

验收配置：

- `AGENT_ENABLED=false`，确保请求进入现有 RAG Pipeline；
- `RAG_AGENTIC_RETRIEVAL_SHADOW_ENABLED=true`；
- Shadow 评估期限 8 秒。

真实 SSE 请求完整产生 `meta`、流式 `message`、`finish` 和 `done` 事件，
当前回答正常结束。硅基流动评估调用在本次验收中超过 8 秒期限，主回答未受影响；
数据库正确记录：

```text
node_type=EVIDENCE_EVALUATION
status=ERROR
duration_ms=8144
```

这同时验证了异步 Trace 传播、超时边界和失败隔离。成功评估结果及逐任务状态由
自动化测试覆盖，运行时成功结果会写入结构化日志。

## 6. AR2 输入

AR2 可以直接复用 `EvidenceLedger` 和 `EvidenceEvaluation`，加入一次补检索。
在 Shadow 数据表明 8 秒评估期限持续过紧前，不扩大默认期限；超时率将作为
后续灰度参数的依据。
