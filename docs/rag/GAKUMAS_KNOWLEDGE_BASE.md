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

## 幂等导入

```powershell
python scripts/eval/prepare_agentic_retrieval_fixture.py `
  --dataset resources/eval/agentic-retrieval/v1/gakumas-fixture.json `
  --knowledge-base-name gakumas-agentic-demo-v1 `
  --collection-name gakumas-agentic-demo-v1
```

脚本会复用同名知识库、已成功文档和意图节点，不重复上传。

## 下一步

1. 使用 5 条问题分别跑 Single Pass 和 Agentic Active；
2. 统计 Recall、P95、额外模型调用和引用覆盖；
3. 单独创建管理员知识库导入 restricted 文档；
4. 用普通用户验证首轮、补检索和全文扩展均无法泄漏；
5. 根据结果决定是否进入 Shadow 100%。
