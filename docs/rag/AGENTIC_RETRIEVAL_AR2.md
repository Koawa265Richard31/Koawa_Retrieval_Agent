# Agentic Retrieval AR2 实现与验收记录

## 1. 阶段结论

AR2 已在 Shadow 模式建立“评估证据，不足时最多补检索一次，再次评估”的最小闭环。
Shadow 结果只用于 Trace 和结构化日志，当前用户回答仍使用首轮
`RetrievalContext`，因此可通过关闭一个配置立即回滚。

## 2. 闭环

```text
首轮 RetrievalContext
  -> 建立 RetrievalPlan / EvidenceLedger
  -> Evidence Evaluation
  -> 充分：停止
  -> 不足：生成 Follow-up Plan
  -> 预算、取消、超时、重复查询检查
  -> 最多一次补检索
  -> Evidence 去重合并
  -> 再次 Evaluation
  -> SUFFICIENT / BUDGET_EXHAUSTED / 其他明确停止原因
```

## 3. 已实现内容

- `RuleBasedRetrievalTaskPlanner`：根据证据缺口生成有界补检索查询。
- `LlmRetrievalTaskPlanner` 和 `RetrievalPlanParser`：严格 JSON、已知 taskId、
  重复 taskId 和最大查询数校验。
- `RetrievalPlanFactory`：稳定维护 task、原 `SubQuestionIntent`、知识库/MCP
  `NodeScore` 路由之间的关系。
- `DefaultAgenticRetrievalOrchestrator`：最多两轮，负责预算、停止原因、证据和
  上下文合并。
- `AgenticRetrievalIterationExecutor`：独立有界线程池，按 100ms 轮询取消，
  到达总截止时间后取消 Future。
- Evaluator 和 Planner 的调用截止时间均取“组件超时”和“总截止时间”的较早值。
- 第二轮失败、Planner/Evaluator 失败、队列拒绝均被隔离，不改变用户回答。
- 仅当改写结果至少包含两个子问题时进入 AR2 Shadow；单子问题保持零额外模型调用。
  这是 AR2 的保守门槛，AR3 将用正式复杂度路由替换。

## 4. 配置

默认配置：

```yaml
rag:
  agentic-retrieval:
    shadow-enabled: false
    timeout: 8s
    evaluator-timeout: 8s
    planner-timeout: 8s
    max-iterations: 2
    max-sub-queries: 6
    max-retrieved-chunks: 40
```

所有配置均已映射到 Docker Compose 环境变量，默认关闭。

## 5. 自动化验收

Agentic Retrieval 领域测试覆盖：

- 首轮充分，不触发补检索；
- 首轮不足，第二轮增加新 Evidence 后变为充分；
- 第二轮没有新 Evidence；
- 重复查询；
- Chunk 预算耗尽；
- 总超时返回已有 Evidence；
- 调用取消；
- 第二轮检索异常回退首轮上下文；
- Evaluator deadline 正确分类为 `TIMEOUT`；
- Planner 合法、非法和未知任务输出；
- 限时执行器成功、超时和取消；
- 单子问题不增加模型调用；
- Shadow 异常不影响主链路。

其中“首轮 chunk-1 不足，补检索得到 chunk-2 后充分”的编排器测试，是本阶段
可解释的检索改善样例。最大迭代次数由 `RetrievalBudget` 和编排器双重限制为 2，
不存在无限重试路径。

## 6. Docker 真实链路验收

真实环境临时使用 30 秒总预算、Evaluator/Planner 各 10 秒，保留
`AGENT_ENABLED=false` 和 Shadow 开启：

- SSE 返回 HTTP 200；
- `meta`、`finish`、`done` 事件完整；
- 用户回答正常结束；
- 硅基流动 Evaluator 达到 10 秒预算后，Shadow 以 `TIMEOUT` 停止；
- Trace 正确记录 `EVIDENCE_EVALUATION` 和 `RETRIEVAL_ITERATION`；
- 日志记录 `iterations=1`、任务状态和 Evidence 数量；
- 超时没有传播到主问答。

第二轮成功闭环由确定性自动化测试验证；真实供应商超时样例验证了生产装配、
总预算和降级隔离。

## 7. AR3 输入

AR3 不修改编排器，只在其前面增加正式的 `RetrievalComplexityDecider` 和稳定
会话灰度。当前“至少两个子问题”的保守门槛将作为规则特征之一，而不是最终路由。
