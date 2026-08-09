# 管理控制台 · 学园偶像大师风格改造规划书

> 版本：v1（2026-08-10）
> 范围：`frontend/src/pages/admin/**` + `globals.css` 中 `.admin-*` / `.trace-*` 样式层
> 基准：`frontend/DESIGN.md`（粉丝页设计系统）＋ `gakuen-idolmaster-ui-art` skill 视觉铁律
> 本轮已做：全局 chrome（侧栏/顶栏/面包屑/主按钮/统计卡图标）已切到学偶暖色，作为 L0 基线；本文用于指导后续分批落地并逐项验收。

---

## 1. 目标与视觉铁律（不变量）

1. 品牌橙 `#F39800` / `#FF7600` 只做点缀：CTA、激活态、徽标、焦点环、选中指示；底色保持白/暖奶油 `#FFF7EC` 系，禁止大面积纯橙。
2. 墨绿 `#407040` 只做低透明度氛围层（顶部光晕/背景纹理），禁止大面积纯绿。
3. 斜切造型 `0 43.89px 0 43.89px` 只用于主 CTA（发送/新建/保存主按钮），其余控件圆角 8–12px。
4. 字体统一 IBM Plex Sans JP（已全局引入 400/500/600/700），系统兜底。
5. 暖粉 `#F0C0C0`/`#C07070` 做选中底、hover、装饰。
6. 装饰克制：纹样 `loading/bg_pc.png` 透明度 ≤0.16、logo 水印 ≤0.05、旋钮/壁纸低透明，不干扰正文。
7. 所有动效尊重 `prefers-reduced-motion`。
8. 样式作用域隔离：管理台改动只落在 `.admin-*` / `.trace-*` 类与 `.admin-layout` 作用域内，不污染 `/chat`、`/fan`、登录页。

## 2. 现状盘点

### 2.1 全局 chrome（本轮已完成 L0 基线）
| 模块 | 现状 | 本轮状态 |
|---|---|---|
| 侧栏 `.admin-sidebar` | 暖奶油 rail + 品牌橙强调 + logo_main.jpg | ✅ 已改 |
| 顶栏 `.admin-topbar` | 白/奶油 + 模糊 | ✅ 已改 |
| 面包屑 `.admin-breadcrumbs` | 橙 hover | ✅ 已改 |
| 链接 `.admin-link` | 橙 | ✅ 已改 |
| 主按钮 `.ui-button[data-variant=default]` | 橙底白字 | ✅ 已改 |
| 统计卡 `.admin-stat-*` | 橙图标 + 白卡 | ✅ 已改 |

### 2.2 共享 UI 组件（`.admin-layout` 作用域，globals.css）
| 组件 | 现状 | 差距 |
|---|---|---|
| `ui-card` / `ui-card-header/title/content` | 白卡 + 浅边框 | 基本契合；标题可加暖橙 eyebrow 点缀 |
| `ui-table` / head/row/cell | 浅灰表头 + 斑马纹 | 表头可换暖奶油 `#FFF7EC`，hover 换 `#FFF3E0` |
| `ui-badge[data-variant=default]` | 靛蓝系 | 应改暖橙系；secondary/outline 保留中性 |
| `ui-button` outline/ghost | 中性 | 可加橙色 hover/焦点环 |
| `ui-input` / `ui-select-trigger` | 中性 | 焦点环改橙（`--ring: 33 100% 50%` 已设，需核对 focus 类） |
| `ui-dialog/alert-dialog` | 白卡 | 契合；标题区可加暖橙分隔 |
| Tabs（各页自带 tab 类） | 蓝/灰 | 需统一切暖橙激活态（见 2.4） |

### 2.3 硬编码色问题清单（必须 token 化）
| 位置 | 硬编码 | 处理 |
|---|---|---|
| `DashboardPage.tsx` KPICardItem `iconBg/iconColor` | `#DBEAFE/#2563EB`、`#E0E7FF/#4F46E5`、`#FEF3C7/#D97706`、`#E0F2FE/#0284C7` | 换成语义色（橙/暖粉/奶油/墨绿低透明），见 §3 |
| `IntentListPage.tsx` 状态色 | `#1890FF/#52C41A/#FA8C16/#d9d9d9/#91d5ff/#b7eb8f/...`（antd 蓝绿橙） | 映射到暖色语义集：橙=运行/待办、暖绿=成功、暖粉=预警、灰=停用 |
| `trace-*` 变量块 | 中性 slate（`#0f172a/#64748b/#f8fafc/...`） | 在 `.admin-layout .trace-list-page` 变量块内覆盖为暖色系（见 §4 L4） |
| 各页 `text-slate-*` 直用 | 中性灰 | 可保留（中性文字合理），但重点区（标题/指标）可换 `#1a1a1a` |

### 2.4 页面清单与各自要点
| 页面 | 结构 | 改造要点 |
|---|---|---|
| `dashboard/DashboardPage` | KPI 卡 + 面积图 + 时间窗切换 | ①KPI 卡 icon token 化 ②图表主色橙 `#FF7600`、渐变淡橙 ③时间窗控件暖色激活 ④空态/加载态用 logo 水印 |
| `knowledge/KnowledgeListPage` | 统计条 + 表格 + 创建/重命名/删除弹窗 | 统计条换橙；collection badge 暖色；表格头暖奶油 |
| `knowledge/KnowledgeDocumentsPage` | 文档表格 + 上传/分块 + 搜索 | 上传按钮主 CTA 斜切；分块进度用橙色 |
| `knowledge/KnowledgeChunksPage` | 分块列表 + 处理步骤 | 步骤条暖橙激活态；代码/元信息中性 |
| `ingestion/IngestionPage` | 流水线/任务双 tab + 状态卡 | tab 激活橙；流水线状态徽标暖色 |
| `intent-tree/IntentTreePage` | 树形结构 | 树节点选中暖粉底+橙条；连线中性 |
| `intent-tree/IntentListPage` | 列表 + 状态徽标 | 状态色 token 化（见 2.3） |
| `intent-tree/IntentEditPage` | 表单 | 表单焦点环橙；主保存按钮斜切 |
| `query-term-mapping/QueryTermMappingPage` | 映射表 | 表格暖色；空态提示 |
| `sample-questions/SampleQuestionPage` | 示例问题 CRUD | 表格 + 主按钮斜切 |
| `settings/SystemSettingsPage` | 表单/开关 | 开关激活橙；卡片暖奶油描边 |
| `users/UserListPage` | 用户表 + 角色徽标 | 徽标暖色；表格头暖奶油 |
| `traces/RagTracePage` + components | 自绘 KPI/表格（`--trace-*`） | 变量块暖色化（§4） |
| `traces/RagTraceDetailPage` | 链路详情/节点 | 节点状态色暖化；时间线强调橙 |

## 3. 设计 Token（建议加入 `globals.css` 的 `.admin-layout`）

```css
.admin-layout {
  /* 语义色 */
  --adm-brand: #f39800;      /* 品牌橙：徽标/激活 */
  --adm-accent: #ff7600;     /* CTA/焦点环/链接 */
  --adm-cream: #fff7ec;      /* rail/卡片氛围 */
  --adm-cream-deep: #fff3e2; /* 表头/hover */
  --adm-blush: #fdeed8;      /* 选中底/图标底 */
  --adm-pink: #f0c0c0;       /* 装饰/预警底 */
  --adm-stage: #407040;      /* 墨绿：仅低透明氛围 */
  --adm-ink: #1a1a1a;
  --adm-muted: #6e6e73;
  --adm-line: #e8e9eb;
  --adm-success: #2f9e44;
  --adm-warn: #f39800;
  --adm-danger: #c0392b;
}
```
规则：
- KPI 图标底 = `--adm-blush`，图标 = `--adm-accent`；趋势涨 = `--adm-success`，跌 = `--adm-danger`。
- 表格头 = `--adm-cream-deep`；行 hover = `--adm-cream`。
- 状态徽标：成功=`#e8f5e9/#2f9e44`，进行/运行=`#fff3e2/#e07b00`，警告=`#fdeed8/#c07070`，停用=`#f5f5f5/#6e6e73`。
- 焦点环统一 `--adm-accent` 35% 透明。

## 4. 实施顺序（每批可独立验证、可独立上线）

### 批次 A：共享层统一（低风险，先做）
- `globals.css`：`.admin-layout` 内补 §3 token；覆盖 `ui-table-header`、`ui-badge` 默认变体、`ui-input/select` 焦点、Tabs 激活类。
- 产出验证：知识库列表页打开，表格/徽标/输入框焦点均为暖色，无回归。

### 批次 B：Dashboard
- `DashboardPage.tsx`：KPI 卡 icon 改语义 token；面积图 stroke/fill 改橙；时间窗按钮激活态暖色；空态加 logo 水印。
- 产出验证：运营概览页 KPI/图表为学偶暖色，数字可读性不变。

### 批次 C：业务页（知识库/意图/数据通道/映射/示例/设置/用户）
- 逐页：表格头、徽标、tab 激活、主按钮斜切、空态。
- 优先高使用页：KnowledgeList → KnowledgeDocuments → KnowledgeChunks → Ingestion → IntentList（含状态色 token 化）→ 其余。
- 产出验证：逐页走查 + `web-design-guidelines` 审计。

### 批次 D：Trace 链路页
- `globals.css` 中 `.admin-layout .trace-list-page` 变量块：把 `--trace-*` 主色/边框/图标底改为暖色系；节点状态色映射同 §3 语义集。
- 产出验证：RagTracePage / Detail 打开，KPI/表格/节点为暖色。

### 批次 E：动效与细节
- 页面内容进入动画（轻上浮淡入，`prefers-reduced-motion` 关闭）；侧栏展开动画；按钮 hover 微交互；空状态统一（logo 水印 + 暖橙插画文字）。
- 产出验证：浏览器实测动画流畅、无跳动；关闭动画偏好时全部静止。

## 5. 验收清单（每批合并前勾选）

- [ ] 品牌橙只出现在 CTA/徽标/激活/焦点，底色白/奶油
- [ ] 墨绿仅低透明氛围层
- [ ] 斜切仅主 CTA，其余 8–12px 圆角
- [ ] 无新增硬编码 hex（色值全部走 token 或语义类）
- [ ] 表格/徽标/状态色/图表为暖色语义集
- [ ] 字体 IBM Plex Sans JP 兜底
- [ ] `prefers-reduced-motion` 下无动画
- [ ] `npm run lint` + `npx vite build --outDir dist-check` 通过
- [ ] `web-design-guidelines` 逐文件审计无新增违规
- [ ] `web-design-reviewer` 浏览器实测：Dashboard、知识库列表/文档/分块、Ingestion、Intent、Trace、Settings、Users 各页截图核对
- [ ] 回归：`/chat`、`/fan`、登录页样式未被影响

## 6. 边界与不变量
- 不改后端 API、不改数据结构；纯前端样式/文案层。
- 所有类名沿用现有 `.admin-*` / `.trace-*` / `ui-*`，只在 `.admin-layout` 作用域覆盖，不新增全局污染。
- 动效尊重系统减少动态偏好。
- 管理台品牌文案保持「学园偶像大师 / 同好知识管理控制台」（已在本轮设置），不引入外部品牌。
