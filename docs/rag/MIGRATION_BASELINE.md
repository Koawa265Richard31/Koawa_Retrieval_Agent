# Spring AI 迁移前基线

记录时间：2026-07-23

## 1. 基线目的

这份记录用于回答两个不同的问题：

1. 当前代码在不启动外部基础设施时，能否完成编译和单元测试；
2. 当前 RAG 链路在固定数据集上的检索与回答质量如何。

两类基线必须分开。构建通过不代表 RAG 质量合格，Redis、PostgreSQL、Milvus 等集成测试失败也不等于纯 Java 单元逻辑错误。

## 2. 仓库与运行环境

- 仓库：`D:\ragent`
- Spring Boot：3.5.7
- 项目编译目标：Java 17
- 本次 Maven 实际运行 JDK：Java 21
- 命令：`mvn test`
- 外部基础设施：未启动
- XQuAD 临时脚本和本地下载数据：已清理

## 3. 完整测试结果

结果：`BUILD FAILURE`

| 模块 | 测试数 | 断言失败 | 环境/启动错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| bootstrap | 115 | 0 | 22 | 0 |
| framework | 0 | 0 | 0 | 0 |
| infra-ai | 0 | 0 | 0 | 0 |
| mcp-server | 未执行 | - | - | - |

可确认的结论：

- 93 个测试通过；
- 没有业务断言失败；
- 22 个错误来自 11 个 `@SpringBootTest` 测试类；
- 首个根因是 Spring 上下文创建 `RedissonClient` 时无法连接 `127.0.0.1:6379`；
- Spring Test 的 context failure threshold 随后让共用失败上下文的其他用例直接报错；
- 因 bootstrap 模块失败，Maven Reactor 没有继续执行 mcp-server。

受影响的测试套件：

- `KoawaAgentApplicationTests`
- `InvoiceIndexDocumentTests`
- `ConversationMessageServiceTests`
- `MilvusCollectionTests`
- `PgVectorStoreServiceTest`
- `SiliconFlowEmbeddingServiceTests`
- `VectorTreeIntentClassifierTests`
- `IntentTreeServiceTests`
- `SimpleIntentClassifierTests`
- `MultiQuestionRewriteServiceTests`
- `QueryRewriteTests`

### 3.1 隔离后的离线基线

上述 11 个套件已统一标记为 JUnit `integration`，默认 Surefire 排除该分组；需要完整环境时使用：

```shell
mvn test -Pintegration
```

调整后的默认命令再次执行：

```shell
mvn test
```

结果：`BUILD SUCCESS`

- 5 个 Reactor 模块全部成功；
- 93 个离线测试全部通过；
- Failures：0；
- Errors：0；
- Skipped：0；
- 总耗时：17.781 秒。

受限执行环境曾因无权清理用户 Temp 目录而产生 5 个 JUnit `AccessDeniedException`；使用正常本机权限重跑后全部通过，确认不是代码或测试夹具错误。

## 4. 基线暴露的问题

### 4.1 默认测试与集成测试没有隔离

当前 `mvn test` 会直接执行依赖 Redis、数据库、向量库和外部模型的测试。开发机未启动完整环境时，默认测试无法形成稳定的快速反馈。

后续应明确两条命令：

- 默认单元测试：不依赖网络和中间件，必须稳定通过；
- 集成测试：通过 Maven profile 或 Failsafe 单独执行，并明确需要的 Docker Compose 环境与密钥。

不能通过吞异常、删除断言或伪造外部服务来把默认构建“刷绿”。

### 4.2 部分模块缺少测试

`framework` 与 `infra-ai` 本次均显示 0 个测试。Spring AI 适配首先会进入 `infra-ai`，因此迁移前要为协议映射、模型路由与回退建立纯单元测试。

### 4.3 Maven 构建存在可重复性警告

根 POM 未显式固定 `maven-compiler-plugin` 和 `maven-surefire-plugin` 的版本；资源编码也依赖平台默认值。迁移依赖前应固定插件版本及 `project.build.sourceEncoding=UTF-8`。

### 4.4 尚无可复现的 RAG 质量基线

仓库已有 `EvalController`，但目前没有随仓库版本管理、可一键运行的固定评测集，也没有自动计算 Recall@5、MRR、引用命中率、拒答准确率和延迟分位数的报告。

之前下载的 XQuAD 更偏通用问答，并不能代表企业知识库业务，因此不继续把它作为核心评测集。

## 5. 下一阶段准入条件

Spring AI 第一批适配代码开始前：

- 保留本文件记录的失败结果，不把环境错误误判成迁移回归；
- 固定 Maven 插件版本和 UTF-8 编码；
- 将纯单元测试与外部依赖集成测试隔离；
- 给 `infra-ai` 的第一层协议转换补充纯单元测试。

以上四项已于 2026-07-23 完成。首个 Spring AI 切片新增 5 个 `infra-ai` 单元测试，覆盖 Prompt 映射、同步调用和空响应分类；它不注册 Spring Bean，也没有改变现有模型路由。

切片完成后的全仓离线验证：

- 命令：`mvn test`
- 结果：`BUILD SUCCESS`
- 总测试数：98（bootstrap 93，infra-ai 5）
- Failures：0
- Errors：0
- 5 个 Reactor 模块全部成功

模型适配切流前：

- 建立至少 50 条面向企业知识库的固定问题集；
- 每条数据至少记录问题、期望文档/分块、答案要点、是否应拒答；
- 同一份数据分别跑旧实现与 Spring AI 实现；
- 只有关键指标不退化且可回滚，才允许默认切换到 Spring AI adapter。

## 6. 数据清理说明

已删除：

- `D:\ragent\scripts\kb`
- `D:\ragent-local-data\xquad`

2026-07-23 已临时启动 PostgreSQL，并在单个事务中物理删除 XQuAD 知识库及其关联数据：

- 知识库：1 条；
- 文档：20 条；
- 分块：20 条；
- 分块日志：20 条；
- pgvector 向量：20 条；
- 调度及调度执行记录：0 条。

提交后按知识库 ID 和 `xquad-zh-demo` collection 复查，知识库、文档、分块与向量命中数均为 0。`deploy/runtime/rustfs/xquad-zh-demo` 属于对象存储残留，本次数据库清理未删除。
