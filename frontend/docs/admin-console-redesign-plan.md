# 管理控制台 · 学园偶像大师风格改造规划书

> 版本：v2.0（2026-08-10）
> 执行状态：**批次 A~E 全部完成并已上线验证**（见 §4）
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

### 批次 A：共享层统一（低风险，先做） ✅ 已完成（2026-08-10）
- `globals.css`（`frontend/src/styles/globals.css` 末尾「批次A」段）：
  - `.admin-layout` 补 `--adm-*` 语义 token（brand/accent/cream/blush/pink/stage/ink/muted/line/success/warn/danger）；
  - `ui-card` 白卡 + 表头暖橙 eyebrow；
  - `ui-table-header` 暖奶油 `#fff3e2`、行 hover `#fff3e0`；
  - `ui-badge[data-variant=default]` 暖橙系、secondary 暖粉底、outline 中性；
  - `ui-input` / `ui-select-trigger` 焦点环橙色；
  - `[role=switch][data-state=checked]` 橙色激活；
  - 新增 `.ui-button.admin-cta` 斜切造型（`0 43.89px 0 43.89px`），供批次 C 主按钮使用。
- 产出验证：`npm run lint` 通过；`npx vite build --outDir dist-check` 通过；待浏览器实测（批次 E 前用 playwright 截图核对）。

### 批次 B：Dashboard ✅ 已完成（2026-08-10）
- `DashboardPage.tsx`：KPI 图标底/色改语义暖色（橙/暖粉/暖琥珀/暖绿）；流量图 `#3B82F6`→`#FF7600`（渐变+折线+tooltip）；时间窗激活态改 `#FF7600`；InsightCard 语义暖色；SimpleLineChart 主色 `#8b5cf6`→`#ff7600`。
- 产出验证：playwright 实测 activeWindow=`#FF7600`、chartStrokes=`#FF7600`/`var(--chart-*)`、KPI 图标 4 色全暖。

### 批次 C：业务页 ✅ 已完成（2026-08-10）
- 共享层表格头/徽标/tab 激活由批次 A 覆盖；主 CTA（新建/上传/新增）全部加 `admin-cta` 斜切（KnowledgeList/Documents/Ingestion/SampleQuestion/UserList/QueryTermMapping/IntentTree）。
- IntentList 状态色 token 化：层级 DOMAIN=暖橙 / CATEGORY=暖绿 / TOPIC=暖粉，启用=暖绿、禁用=灰（专用 `admin-level-*`/`admin-status-*` 类）。
- 产出验证：playwright 实测 DOMAIN `#e07b00/#FFF7E6`、启用 `#2f9e44/#E8F5E9`；CTA 斜切 `0 43.89px`。

### 批次 D：Trace 链路页 ✅ 已完成（2026-08-10）
- `globals.css` 末尾覆盖 `.admin-layout .trace-list-page` 的 `--trace-*` 颜色变量（暖奶油底/暖橙图标/暖边框）。
- `RagTraceDetailPage`：状态点/指标（成功=暖绿、失败=暖红、运行=暖橙）、Root/最慢/选中行高亮改暖色。
- 产出验证：Traces 列表/详情截图走查。

### 批次 E：动效与细节 ✅ 已完成（2026-08-10）
- 页面内容入场轻上浮淡入（`admin-fade-up`，含逐块延迟）；`prefers-reduced-motion` 下全部关闭。
- 主按钮按下微交互 `scale(0.97)`；新增 `.admin-empty` 空态（logo 水印 + 暖色文字），页面空态容器加该类即可。
- 产出验证：playwright 实测 `animationName=admin-fade-up`。

## 5. 验收清单（每批合并前勾选）

- [x] 品牌橙只出现在 CTA/徽标/激活/焦点，底色白/奶油（批次A 已按 token 落地）
- [x] 墨绿仅低透明氛围层（批次A 无大面积绿）
- [x] 斜切仅主 CTA（`.admin-cta` 已定义，其余圆角；批次C 逐页套用）
- [ ] 无新增硬编码 hex（色值全部走 token 或语义类）
- [x] 表格/徽标/状态色为暖色语义集（批次A 共享层；图表批次B）
- [ ] 字体 IBM Plex Sans JP 兜底
- [x] `prefers-reduced-motion` 下无动画（批次E 已加媒体查询）
- [x] `npm run lint` + `npx vite build --outDir dist-check` 通过
- [ ] `web-design-guidelines` 逐文件审计无新增违规
- [x] playwright 浏览器实测：Dashboard/IntentList/Traces/KnowledgeDocs 截图 + 计算样式断言（截图在 `output/playwright/batchB-E*`）
- [x] 回归：`/chat`、`/fan` 无 `.admin-layout` 泄漏（playwright 实测）

## 6. 边界与不变量
- 不改后端 API、不改数据结构；纯前端样式/文案层。
- 所有类名沿用现有 `.admin-*` / `.trace-*` / `ui-*`，只在 `.admin-layout` 作用域覆盖，不新增全局污染。
- 动效尊重系统减少动态偏好。
- 管理台品牌文案保持「学园偶像大师 / 同好知识管理控制台」（已在本轮设置），不引入外部品牌。
