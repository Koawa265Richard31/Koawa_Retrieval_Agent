# 学园偶像大师 Agentic Retrieval 演示知识库

## 数据组成

- 4 篇中文维基百科真实页面，通过 MediaWiki Action API 获取；
- 3 篇公开合成业务资料，用于版本对比、多跳和冲突；
- 1 篇不导入公开库的管理员受限资料，用于 ACL 回归；
- 5 条评测问题，覆盖 simple、complex、conflict 和 ACL。

维基文件保留页面 URL、revision ID、revision timestamp、抓取时间及 CC BY-SA
归因。重新生成使用：

```powershell
python scripts/demo/prepare_gakumas_knowledge_base.py `
  --output resources/demo/gakumas-kb/wikipedia
```

## 实际入库结果

2026-07-24 在本地 Docker 环境完成：

- 知识库：`gakumas-agentic-demo-v1`；
- Knowledge Base ID：`2080636521416810496`；
- 意图节点：`demo-gakumas`；
- 成功文档：7/7；
- 向量 Chunk：16；
- 存储：PostgreSQL + pgvector。

受限文档没有导入公开知识库。

## 真实召回冒烟

问题：

> 新版练习赛和旧版相比，回合数、同分规则和新手保护期分别有什么变化？

结果：

- 子意图路由到 `demo-gakumas`；
- 首位召回 `contest-rules-v2`；
- 第二位召回 `contest-rules-v1`；
- 第三位召回 `event-and-reward`；
- 端到端评测接口耗时 23,562 ms。

这证明 Query Rewrite、Intent Resolver、知识库 Collection 路由、Embedding 和
pgvector 召回已经连接到新数据。23.6 秒仍明显偏慢，需在灰度前拆分模型调用和
检索耗时，不能直接作为 Active 的可接受延迟。

## Single Pass / Agentic 对比结果

2026-07-24 使用固定 5 题在本地 Docker 环境完成对比。首次使用统一的
8 秒 Agentic 总预算时，5/5 均在第一次证据评估超时并安全回退。根据真实供应商
延迟，将预算调整为 evaluator 45 秒、planner 30 秒、整轮 120 秒后重新执行：

- 4 条有目标来源的问题，Single Pass 平均来源召回率为 100%；
- Agentic Active 平均来源召回率同为 100%，没有产生额外召回增益；
- Single Pass P95 为 51,719 ms，Active P95 为 48,644 ms；
- 4/5 以 `SUFFICIENT` 正常完成，Active 回退率从 100% 降至 20%；
- ACL 题以 `EVALUATION_FAILED` 回退，需要在真实权限数据下单独诊断；
- 本轮生产复杂度规则未命中问题，Active 是评测接口强制执行；
- 引用目录均指向本次实际召回的 Chunk；
- ACL 未验证：评测脚本使用管理员账号，受限文档也尚未导入独立知识库。

扩大预算解决了固定 8 秒导致的主要超时，但平均额外延迟为 10,622 ms，且没有
提升来源召回率，因此仍不能宣称 Agentic Retrieval 带来质量提升。进入 Shadow
或 Active 灰度前，需要完成普通用户 ACL 回归、修正复杂度路由样例，并为证据
评估和规划选择更低延迟的模型。

完整机器可读报告：

```text
resources/eval/agentic-retrieval/v1/gakumas-comparison-summary.json
```

## 幂等导入

```powershell
python scripts/eval/prepare_agentic_retrieval_fixture.py `
  --dataset resources/eval/agentic-retrieval/v1/gakumas-fixture.json `
  --knowledge-base-name gakumas-agentic-demo-v1 `
  --collection-name gakumas-agentic-demo-v1
```

脚本会复用同名知识库、已成功文档和意图节点，不重复上传。

## 下一步

1. 为 ACL 用例建立普通用户与管理员两个独立评测身份；
2. 修正复杂度路由样例，使固定复杂题能稳定进入 Agentic；
3. 单独创建管理员知识库导入 restricted 文档；
4. 用普通用户验证首轮、补检索和全文扩展均无法泄漏；
5. 根据结果决定是否进入 Shadow 100%。
