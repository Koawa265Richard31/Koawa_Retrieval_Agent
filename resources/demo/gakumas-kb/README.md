# 学园偶像大师演示知识库

本目录混合两类资料：

- `wikipedia/`：由脚本通过中文维基百科 MediaWiki API 获取，保留 URL、修订号、
  抓取时间和 CC BY-SA 归因；
- `synthetic/`：专为多跳、版本冲突、全文扩展和 ACL 测试编写，均明确声明为虚构。

生成真实语料：

```powershell
python scripts/demo/prepare_gakumas_knowledge_base.py `
  --output resources/demo/gakumas-kb/wikipedia
```

禁止把 `synthetic-restricted` 文档与普通公开知识库放入同一 ACL 域。测试时应创建
独立的管理员知识库，并使用普通用户确认首轮检索、补检索、引用和全文扩展均不泄漏。

服务启动后，可用现有公开 API 导入公开库：

```powershell
python scripts/eval/prepare_agentic_retrieval_fixture.py `
  --dataset resources/eval/agentic-retrieval/v1/gakumas-fixture.json `
  --knowledge-base-name gakumas-agentic-demo-v1 `
  --collection-name gakumas-agentic-demo-v1
```

受限文档不由该命令自动导入，必须使用管理员单独创建知识库，以免测试数据准备过程
本身破坏 ACL 隔离。
