import * as React from "react";
import {
  ArrowUp,
  BookOpen,
  CircleUserRound,
  Clock3,
  Menu,
  MessageCircle,
  Mic2,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Search,
  Sparkles,
  X
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

const topics = ["今日的练习安排", "P卡和支援卡怎么搭配？", "帮我介绍一下咲季", "活动剧情回顾"];
type FanAvatar = {
  name: string;
  src: string;
};

const baseIdols = ["倉本千奈", "月村手毬", "藤田ことね", "有村麻央", "花海咲季", "葛城リーリヤ", "篠澤広", "紫雲清夏", "姫崎莉波"];
const fanAvatarAssets: FanAvatar[] = [
  ...baseIdols.map((name, index) => ({ name, src: `/assets/gakuen/distribution/icon/icon_${String.fromCharCode(65 + index)}.png` })),
  ...[...baseIdols, "花海佑芽", "秦谷美鈴", "十王星南"].map((name, index) => ({ name, src: `/assets/gakuen/distribution/icon/halloween_icon_${index + 1}.png` }))
];

function randomFanAvatar() {
  return fanAvatarAssets[Math.floor(Math.random() * fanAvatarAssets.length)];
}

function LoadingOverlay({ leaving }: { leaving: boolean }) {
  return (
    <div className={`fan-loading ${leaving ? "is-leaving" : ""}`} aria-live="polite">
      <div className="fan-loading-card">
        <img src="/assets/gakuen/logo_text_or.svg" alt="学园偶像大师" />
        <div className="fan-loading-dots" aria-label="正在加载"><i /><i /><i /></div>
        <span>Loading</span>
      </div>
    </div>
  );
}

export function FanChatPage() {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const { messages, isStreaming, sendMessage, cancelGeneration, createSession } = useChatStore();
  const [value, setValue] = React.useState("");
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(false);
  const [loading, setLoading] = React.useState(true);
  const [hasStarted, setHasStarted] = React.useState(false);
  const [assistantAvatar, setAssistantAvatar] = React.useState(randomFanAvatar);
  const inputRef = React.useRef<HTMLTextAreaElement | null>(null);
  const visibleMessages = messages.filter((message) => message.content || message.thinking);
  const hasConversation = visibleMessages.length > 0;

  React.useEffect(() => {
    const timer = window.setTimeout(() => setLoading(false), 720);
    return () => window.clearTimeout(timer);
  }, []);

  const submit = async () => {
    if (isStreaming) {
      cancelGeneration();
      return;
    }
    const question = value.trim();
    if (!question) return;
    setValue("");
    setHasStarted(true);
    await sendMessage(question);
  };

  const setTopic = (topic: string) => {
    setValue(topic);
    inputRef.current?.focus();
  };

  const startNewChat = async () => {
    await createSession();
    setAssistantAvatar(randomFanAvatar());
    setHasStarted(false);
    setValue("");
    inputRef.current?.focus();
  };

  const closeSidebar = () => {
    if (window.matchMedia("(max-width: 900px)").matches) {
      setSidebarOpen(false);
      return;
    }
    setSidebarCollapsed(true);
  };

  return (
    <main className={`fan-page ${sidebarCollapsed ? "is-sidebar-collapsed" : ""}`}>
      {loading ? <LoadingOverlay leaving={false} /> : null}
      {sidebarCollapsed ? <button type="button" className="fan-sidebar-reopen fan-icon-control" title="展开导航" aria-label="展开导航" onClick={() => setSidebarCollapsed(false)}><PanelLeftOpen /></button> : null}
      <aside className={`fan-sidebar ${sidebarOpen ? "is-open" : ""}`}>
        <div className="fan-sidebar-top">
          <button type="button" className="fan-wordmark" onClick={() => navigate("/fan")}>
            <Sparkles aria-hidden="true" /><span>学园通信</span>
          </button>
          <button type="button" className="fan-icon-control" title="收起导航" aria-label="收起导航" onClick={closeSidebar}><PanelLeftClose /></button>
        </div>
        <button type="button" className="fan-new-chat" onClick={() => void startNewChat()}><Plus />新对话</button>
        <div className="fan-sidebar-feature"><img src="/assets/gakuen/knob.svg" alt="" /><span>今日练习<br /><b>READY TO SHINE</b></span></div>
        <nav className="fan-nav" aria-label="对话导航">
          <p>最近对话</p>
          <button type="button" className="is-active"><MessageCircle />练习室对话</button>
          <button type="button"><Clock3 />本周育成笔记</button>
          <button type="button"><BookOpen />角色与卡牌</button>
        </nav>
        <div className="fan-sidebar-bottom">
          <button type="button" className="fan-profile" onClick={() => navigate("/chat")} title="返回知识工作台">
            <CircleUserRound /><span><b>{user?.username || "同好"}</b><small>知识工作台</small></span>
          </button>
        </div>
      </aside>
      {sidebarOpen ? <button type="button" className="fan-sidebar-backdrop" aria-label="关闭导航" onClick={() => setSidebarOpen(false)} /> : null}

      <section className="fan-shell">
        <header className="fan-mobile-head">
          <button type="button" className="fan-icon-control" aria-label="打开导航" onClick={() => setSidebarOpen(true)}><Menu /></button>
          <span>学园通信</span>
          <button type="button" className="fan-icon-control" aria-label="返回工作台" onClick={() => navigate("/chat")}><CircleUserRound /></button>
        </header>
        <section className="fan-conversation" aria-label="练习室对话">
          <header className="fan-conversation-head">
            <div><p className="fan-eyebrow">COMMUNITY CHAT</p><h2>练习室对话</h2></div>
            <button type="button" className="fan-icon-control" title="搜索对话" aria-label="搜索对话"><Search /></button>
          </header>
          <div className="fan-topic-strip">{topics.map((topic) => <button type="button" key={topic} onClick={() => setTopic(topic)}>{topic}</button>)}</div>
          <div className="fan-messages">
            {hasStarted || hasConversation ? <article className="fan-message"><div className="fan-avatar fan-avatar-assistant"><img src={assistantAvatar.src} alt={assistantAvatar.name} /></div><div><span>{assistantAvatar.name}</span><p>欢迎来到练习室。今天也一起为偶像加油吧。角色、P卡、活动和培养攻略，都可以问我。</p></div></article> : null}
            {visibleMessages.map((message) => <article key={message.id} className={`fan-message ${message.role === "user" ? "fan-message-user" : ""}`}><div className={`fan-avatar ${message.role === "assistant" ? "fan-avatar-assistant" : "fan-avatar-producer"}`}>{message.role === "user" ? <img src="/assets/gakuen/logo_text_or.svg" alt="プロデューサー" /> : <img src={assistantAvatar.src} alt={assistantAvatar.name} />}</div><div><span>{message.role === "user" ? "プロデューサー" : assistantAvatar.name}</span><p>{message.content || message.thinking}</p></div></article>)}
            {isStreaming ? <div className="fan-typing"><i /><i /><i /><span>正在查阅资料</span></div> : null}
          </div>
          <div className={`fan-composer ${hasStarted || hasConversation ? "is-docked" : "is-hero"}`}>
            <textarea ref={inputRef} value={value} onChange={(event) => setValue(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); void submit(); } }} placeholder="" rows={1} />
            <div className="fan-composer-actions"><button type="button" className="fan-icon-control" title="语音输入" aria-label="语音输入"><Mic2 /></button><span>Enter 发送</span><button type="button" className="fan-send" disabled={!value.trim() && !isStreaming} onClick={() => void submit()} aria-label={isStreaming ? "停止生成" : "发送消息"}>{isStreaming ? <X /> : <ArrowUp />}</button></div>
          </div>
          <p className="fan-disclaimer">内容由知识库生成，请结合游戏内实际信息确认。</p>
        </section>
      </section>
    </main>
  );
}
