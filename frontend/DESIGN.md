# 学园通信 Fan Lounge — Design System

> 依据：官网设计系统提取（firecrawl，置信度 0.875）+ 官网实机画面像素取证 + Kimi 布局契约。文档是粉丝页改版的唯一设计基准，改版必须遵循三层 token，禁止硬编码色值。

## 1. References & Evidence

| 来源 | 用途 | 证据 |
| --- | --- | --- |
| `gakuen.idolmaster-official.jp` | 美术方向主基准（品牌色、字体、斜切按钮、调性） | `.firecrawl/gakuen-branding.json`（firecrawl，conf 0.875） |
| 官网实机画面 `top/intro/pic_1*.png`、`top/mv/mv_img.png`、`footer/chara/saki/chara.png` | 游戏实际 UI 参照（已下载） | `.firecrawl/refs/*.png`（像素取证见 §8） |
| `www.kimi.com` | 仅布局契约（左导航 + 居中对话列 + 底部输入）；色值不可信 | `.firecrawl/kimi-branding.json`（conf 0.45，弃用其色值） |

## 2. Design Brief

- 用户：受邀进入的学园偶像大师同好（已登录，走 `/fan`）。
- 主目标：像「练习室」一样的偶像话题聊天——进来就知道能聊角色/P卡/活动/攻略。
- 业务目标：差异化主题体验（登录页保持通用，登录后进入主题世界）；复用现有 RAG 后端。
- 约束：React/Vite/Tailwind/Zustand，无 UI 库，样式作用域 `.fan-*`；答案含 Markdown/图片。
- 调性：playful / high-energy，偶像舞台感，但信息密度和可读性按 Kimi 克制执行。

## 3. IA & Key Flows

```
/fan (FanChatPage)
├─ 左侧栏：学园通信 wordmark / 新对话 / 最近对话列表 / 底部个人入口(知识工作台)
├─ 主区 conversation：
│   ├─ 头部：COMMUNITY CHAT + 练习室对话
│   ├─ 话题条（quick topics）
│   ├─ 消息流（欢迎语 + 用户/助手消息，Markdown 渲染）
│   └─ 底部 composer（Enter 发送 / Shift+Enter 换行 / 流式时变停止）
└─ 移动端：菜单按钮打开侧栏抽屉；头部右按钮回工作台
```

关键流程：登录成功 → `/fan` → 首屏欢迎 + 话题条 → 发送问题 → SSE 流式答案（Markdown/图片）→ 可新建会话 / 切换历史会话。

## 4. Visual Tokens（primitive → semantic → component）

### 4.1 Primitive（原始值，来自官方/取证）

```css
:root {
  /* 官方品牌 */
  --pr-orange-500: #F39800;   /* 官方 primary */
  --pr-orange-600: #FF7600;   /* 官方 accent/link */
  --pr-ink:        #000000;   /* 官方 textPrimary */
  --pr-paper:      #FFFFFF;   /* 官方 background */
  --pr-green-700:  #407040;   /* 实机画面舞台绿（取证） */
  --pr-pink-300:   #F0C0C0;   /* 角色立绘暖粉（取证） */
  --pr-pink-500:   #C07070;   /* 角色立绘玫红（取证） */
  --pr-cream:      #FFF7EC;   /* 暖奶油（承载舞台氛围，inferred） */
  --pr-neutral-600:#6E6E73;   /* 次要文字（inferred） */
  --pr-line:       #E8E9EB;   /* 分隔线（inferred） */
}
```

### 4.2 Semantic（用途别名）

```css
--fan-brand:    var(--pr-orange-500);   /* 品牌主色：激活态/徽标/强调 */
--fan-accent:   var(--pr-orange-600);   /* CTA/链接/焦点环 */
--fan-ink:      var(--pr-ink);          /* 标题/正文 */
--fan-muted:    var(--pr-neutral-600);  /* 元信息/辅助文字 */
--fan-paper:    var(--pr-paper);        /* 对话表面 */
--fan-rail:     var(--pr-cream);        /* 侧栏 */
--fan-stage:    var(--pr-green-700);    /* 舞台/空状态面板（低透明度使用） */
--fan-blush:    var(--pr-pink-300);     /* 选中/悬停暖色底 */
--fan-line:     var(--pr-line);         /* 边框/分隔 */
--fan-danger:   #C0392B;                /* 错误/停止 */
```

### 4.3 Component（组件专用）

```css
--fan-btn-primary-bg:    var(--fan-accent);
--fan-btn-primary-text:  #FFFFFF;
--fan-btn-primary-radius: 0 43.89px 0 43.89px;  /* 官方斜切 */
--fan-btn-ghost-radius:   8px;
--fan-chat-radius:        12px;                 /* 气泡/输入区 */
--fan-shadow-soft:        0 10px 30px rgba(30,35,40,.06);
--fan-focus-ring:         0 0 0 3px rgba(255,118,0,.35);
```

## 5. Typography

- 显示/标题：**IBM Plex Sans JP**（官方字体，Google Fonts 引入 `index.html`）。
- 正文：系统栈 `-apple-system, "Segoe UI", "PingFang SC", sans-serif`。
- 字号：14px 正文 / 12px 元信息 / 24px 页头标题 / 16px 话题条。
- 字距：小写标签 0.12em，其余 0；行高 1.6（正文）/ 1.2（标题）。

## 6. Spacing / Radius / Shadow

- 节奏：4px 基数（官方 baseUnit=4）+ 8px 布局节奏（8/12/16/24/32）。
- 圆角：控件 8px；气泡/输入区 12px；主 CTA 斜切 `0 43.89px 0 43.89px`。
- 阴影：低对比柔和 `0 10px 30px rgba(30,35,40,.06)`；焦点环橙色 3px。

## 7. Component Specs（含状态）

| 组件 | 默认 | Hover | Active/选中 | Disabled | 说明 |
| --- | --- | --- | --- | --- | --- |
| 主 CTA（发送/新对话） | 橙底白字斜切 | 加深 | 按下内凹 | 40% 透明 | 圆角斜切是签名 |
| 幽灵按钮（图标控件） | 透明 | 奶油底 | 橙字 | — | 8px，icon-first |
| 话题条 chip | 白底描边 | 橙描边 | 橙底白字 | — | 首屏与输入区上方 |
| 消息气泡（助手） | 白底 12px | — | — | — | 左对齐 + 头像 |
| 消息气泡（用户） | 橙底白字 12px | — | — | — | 右对齐 |
| 侧栏项 | 透明 | 奶油底 | 橙字+左侧橙条 | — | 会话切换 |
| 流式指示 | 三圆点脉冲 + 「正在查阅资料」 | — | — | — | 跟随发送 |
| Loading 遮罩 | logo_text_or.svg + 圆点 | — | — | — | 720ms 后淡出 |

## 8. Game UI Observations（实机像素取证，量化主导色）

- 实机画面背景：**墨绿舞台绿 `#407040` 系**（intro 两张图 45–49%），配黑色文字与白色信息卡。
- MV/主视觉：**暖橙黄昏 `#F0A070` 系**——与官方品牌橙 `#F39800/#FF7600` 同一色系，印证主色选择。
- 角色立绘（咲季）：墨绿底 + **暖粉/玫红** `#F0C0C0/#C07070`。
- 结论：品牌橙做主 CTA/激活态，墨绿用于空状态/舞台氛围（低透明度叠层），暖粉做选中底色与点缀；避免大面积纯黑，正文用 `--fan-ink`，背景保持白/奶油。

## 9. Motion & Assets

- Loading：`logo_text_or.svg` 居中 + 三点脉冲 + 淡出（`prefers-reduced-motion` 时禁用）。
- `knob.svg` 作为舞台装饰，缓慢旋转；消息输入区无位移。
- 流式：打字指示 + 消息进入轻微上浮淡入。

## 10. States & Edge Cases

- 空状态 ≠ 加载态：首屏欢迎语 + 话题条；加载用遮罩，不改变布局。
- 流式中：发送按钮变停止（X）；禁止重复提交。
- 错误：网络/后端失败显示轻量错误条，可重试；不静默丢消息。
- Markdown/图片：答案必须走 `MarkdownRenderer`；图片失败显示占位/alt。
- 长会话：滚动吸附底部；新消息自动滚到最新。
- 移动端：侧栏抽屉 + 背景遮罩；输入区不被键盘遮挡。

## 11. QA / a11y

- 键盘可达：所有按钮可 Tab 聚焦，焦点环橙色可见。
- ARIA：侧栏 `aria-label="对话导航"`；图标按钮 `title`+`aria-label`；流式 `aria-live="polite"`。
- 对比度：正文 `#000`/`#6E6E73` 在白底 ≥ 4.5:1；橙底白字 CTA 仅用于大号/加粗文本。
- 响应式断点：`900px`（侧栏折叠）、`560px`（话题条横向滚动、头像缩小）。
- 改版后用 `web-design-guidelines` 逐文件审计 + `web-design-reviewer` 浏览器实测。
