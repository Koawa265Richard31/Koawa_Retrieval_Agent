# Agentic Retrieval AR4：引用、冲突与受控全文扩展

## 阶段结果

AR4 在 AR3 Active 链路上增加了可解释证据输出和受控全文扩展：

- 每个真实 `EvidenceItem` 映射为稳定的 `[E1]`、`[E2]` 引用编号；
- 引用目录保留 Chunk、文档、标题和来源 URI 的映射；
- 不在目录中的编号会被判定为无效，不会为缺失来源创建占位引用；
- `TaskEvidenceStatus.CONFLICTED` 会进入回答上下文，要求模型明确陈述分歧；
- Evaluator 只有输出精确标记 `FULL_DOCUMENT_CONTEXT` 才可请求全文扩展；
- 全文扩展执行独立 ACL、归属关系和字符/Chunk 预算检查。

## 引用链路

```text
RetrievedChunk
  -> RetrievalContextEvidenceAdapter
  -> EvidenceItem(documentId/chunkId/source)
  -> EvidenceCitationMapper
  -> [E1] Evidence text
  -> 回答 Prompt：事实性结论必须使用真实编号
```

`RetrievalContext` 同时携带 `citations` 和 `conflictedTaskIds`，后续前端若要把
`[E1]` 渲染成引用卡片，不需要重新推断来源。

当前映射保证“编号来自真实证据”，但流式输出结束后的逐句引用覆盖率统计仍属于
上线观测项；不能仅凭 Prompt 约束宣称模型一定为每句话生成引用。

## 冲突语义

Evaluator 将冲突任务标为 `CONFLICTED`。Presenter 不会把多份冲突证据静默拼成
确定结论，而是向回答模型加入强制约束：

- 明确指出证据存在分歧；
- 分别描述不同来源；
- 不输出未经证据支持的唯一结论。

## FullDocumentExpansion 安全边界

扩展不是按模型提供的任意文档 ID 查询，而是只能从已进入 EvidenceLedger 的
命中 Chunk 出发。读取完整文档前必须同时满足：

1. 命中 Chunk 真实存在且已启用；
2. Chunk 的 `docId/kbId` 与 Evidence 完全一致；
3. 文档真实存在、已启用且属于同一知识库；
4. 知识库存在；
5. `DocumentAccessPolicy` 再次授权当前用户；
6. 扩展 Chunk 仍属于同一文档和知识库。

默认策略允许管理员，普通用户仅能读取自己创建的知识库；身份缺失或任何关系
无法确认时 Fail Closed。后续若增加组织共享知识库，应替换
`DocumentAccessPolicy`，不能放宽 `FullDocumentExpander` 内的归属校验。

默认预算：

```yaml
rag:
  agentic-retrieval:
    full-document-expansion-enabled: true
    max-full-document-chars: 12000
```

扩展内容还受 Agentic Retrieval 的 `max-retrieved-chunks` 总预算约束。即时回滚
可设置 `full-document-expansion-enabled=false`，AR3 的 `mode=off` 仍是整条
Agentic Retrieval 的总回滚开关。

## 验证

- 引用去重、编号稳定、虚构编号拒绝；
- 冲突任务强制生成冲突提示；
- ACL 拒绝后不会查询完整文档 Chunk；
- 只扩展已验证命中文档；
- 超过字符预算时截断；
- 全量 Maven：151 tests，0 failures，0 errors。

## 上线前仍需验证

- 用真实答案回放统计事实句引用覆盖率、引用正确率；
- 使用真实普通用户和管理员账户验证知识库 ACL；
- 验证前端对 `[E<n>]` 的展示和来源跳转；
- 采集全文扩展后的 P95、Prompt token 和内存变化。
