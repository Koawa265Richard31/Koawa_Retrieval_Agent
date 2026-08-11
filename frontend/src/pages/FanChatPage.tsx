import * as React from "react";
import {
  ArrowUp,
  CircleUserRound,
  Menu,
  MessageCircle,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Sparkles,
  X
} from "lucide-react";
import { Link } from "react-router-dom";

import { MarkdownRenderer } from "@/components/chat/MarkdownRenderer";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

const topics = ["今日的练习安排", "P卡和支援卡怎么搭配？", "帮我介绍一下咲季", "活动剧情回顾"];

type FanAvatar = {
  name: string;
  src: string;
};

const fanIdolAvatars: FanAvatar[] = [
  { name: "花海咲季", src: "/assets/gakuen/distribution/icon/花海咲季.png" },
  { name: "月村手毬", src: "/assets/gakuen/distribution/icon/月村手毬.png" },
  { name: "藤田琴音", src: "/assets/gakuen/distribution/icon/藤田琴音.png" },
  { name: "姫崎莉波", src: "/assets/gakuen/distribution/icon/姫崎莉波.png" },
  { name: "紫云 清夏", src: "/assets/gakuen/distribution/icon/紫云 清夏.png" },
  { name: "篠泽 广", src: "/assets/gakuen/distribution/icon/篠泽 广.png" },
  { name: "葛城莉莉娅", src: "/assets/gakuen/distribution/icon/葛城莉莉娅.png" },
  { name: "仓本千奈", src: "/assets/gakuen/distribution/icon/仓本千奈.png" },
  { name: "有村麻央", src: "/assets/gakuen/distribution/icon/有村麻央.png" }
];

const fanHalloweenAvatars: FanAvatar[] = [
  { name: "花海咲季", src: "/assets/gakuen/distribution/icon/花海咲季halloween.png" },
  { name: "月村手毬", src: "/assets/gakuen/distribution/icon/月村手毬1.png" },
  { name: "藤田琴音", src: "/assets/gakuen/distribution/icon/藤田琴音1.png" },
  { name: "有村麻央", src: "/assets/gakuen/distribution/icon/有村麻央1.png" },
  { name: "葛城莉莉娅", src: "/assets/gakuen/distribution/icon/葛城莉莉娅1.png" },
  { name: "仓本千奈", src: "/assets/gakuen/distribution/icon/仓本千奈1.png" },
  { name: "紫云 清夏", src: "/assets/gakuen/distribution/icon/紫云 清夏1.png" },
  { name: "篠泽 广", src: "/assets/gakuen/distribution/icon/篠泽 广1.png" },
  { name: "姫崎莉波", src: "/assets/gakuen/distribution/icon/姫崎莉波1.png" },
  { name: "花海佑芽", src: "/assets/gakuen/distribution/icon/花海佑芽1.png" },
  { name: "秦谷美铃", src: "/assets/gakuen/distribution/icon/秦谷美铃1.png" },
  { name: "十王星南", src: "/assets/gakuen/distribution/icon/十王星南1.png" }
];

const fanAvatarAssets: FanAvatar[] = [...fanIdolAvatars, ...fanHalloweenAvatars];

function randomFanAvatar() {
  return fanAvatarAssets[Math.floor(Math.random() * fanAvatarAssets.length)];
}

function handleAvatarError(event: React.SyntheticEvent<HTMLImageElement>) {
  const img = event.currentTarget;
  img.onerror = null;
  img.src = "/assets/gakuen/logo_text_or.svg";
}

function LoadingOverlay({ leaving, avatar }: { leaving: boolean; avatar: FanAvatar }) {
  return (
    <div className={`fan-loading ${leaving ? "is-leaving" : ""}`} aria-live="polite">
      <div className="fan-loading-card">
        <div className="fan-loading-avatar">
          <img
            src={avatar.src}
            alt={avatar.name}
            onError={(event) => {
              event.currentTarget.onerror = null;
              event.currentTarget.src = "/assets/gakuen/logo_text_or.svg";
            }}
          />
        </div>
        <img src="/assets/gakuen/logo_text_or.svg" alt="学园偶像大师" />
        <div className="fan-loading-dots" aria-label="正在加载">
          <i />
          <i />
          <i />
        </div>
        <span>Loading…</span>
      </div>
    </div>
  );
}

export function FanChatPage() {
  const user = useAuthStore((state) => state.user);
  const {
    sessions,
    currentSessionId,
    messages,
    isStreaming,
    sendMessage,
    cancelGeneration,
    createSession,
    fetchSessions,
    selectSession,
    executionMode,
    setExecutionMode
  } = useChatStore();
  const [value, setValue] = React.useState("");
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(false);
  const [leaving, setLeaving] = React.useState(false);
  const [loading, setLoading] = React.useState(true);
  const [hasStarted, setHasStarted] = React.useState(false);
  const [assistantAvatar, setAssistantAvatar] = React.useState(randomFanAvatar);
  const inputRef = React.useRef<HTMLTextAreaElement | null>(null);
  const scrollRef = React.useRef<HTMLDivElement | null>(null);

  const visibleMessages = messages.filter((message) => message.content || message.thinking);
  const hasConversation = visibleMessages.length > 0;

  React.useEffect(() => {
    void fetchSessions();
    const timer = window.setTimeout(() => setLeaving(true), 720);
    const hide = window.setTimeout(() => setLoading(false), 1080);
    return () => {
      window.clearTimeout(timer);
      window.clearTimeout(hide);
    };
  }, [fetchSessions]);

  React.useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [visibleMessages.length, isStreaming]);

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

  const openSession = async (sessionId: string) => {
    await selectSession(sessionId);
    setHasStarted(true);
    setSidebarOpen(false);
  };

  return (
    <main className={`fan-page ${sidebarCollapsed ? "is-sidebar-collapsed" : ""}`}>
      {loading ? <LoadingOverlay leaving={leaving} avatar={assistantAvatar} /> : null}
      {sidebarCollapsed ? (
        <button
          type="button"
          className="fan-sidebar-reopen fan-icon-control"
          title="展开导航"
          aria-label="展开导航"
          onClick={() => setSidebarCollapsed(false)}
        >
          <PanelLeftOpen aria-hidden="true" />
        </button>
      ) : null}
      <aside className={`fan-sidebar ${sidebarOpen ? "is-open" : ""}`}>
        <div className="fan-sidebar-top">
          <Link to="/fan" className="fan-wordmark">
            <Sparkles aria-hidden="true" />
            <span>学园通信</span>
          </Link>
          <button
            type="button"
            className="fan-icon-control"
            title="收起导航"
            aria-label="收起导航"
            onClick={closeSidebar}
          >
            <PanelLeftClose aria-hidden="true" />
          </button>
        </div>
        <button type="button" className="fan-new-chat" onClick={() => void startNewChat()}>
          <Plus aria-hidden="true" />
          新对话
        </button>
        <div className="fan-sidebar-feature">
          <img src="/assets/gakuen/knob.svg" alt="" />
          <span>
            今日练习
            <br />
            <b>READY TO SHINE</b>
          </span>
        </div>
        <nav className="fan-nav" aria-label="对话导航">
          <p>最近对话</p>
          {sessions.length === 0 ? <span className="fan-nav-empty">还没有对话，点「新对话」开始吧</span> : null}
          {sessions.map((session) => (
            <button
              key={session.id}
              type="button"
              className={currentSessionId === session.id ? "is-active" : ""}
              onClick={() => void openSession(session.id)}
              title={session.title}
            >
              <MessageCircle aria-hidden="true" />
              <span>{session.title}</span>
            </button>
          ))}
        </nav>
        <div className="fan-sidebar-bottom">
          <Link to="/chat" className="fan-profile" title="返回知识工作台">
            <CircleUserRound aria-hidden="true" />
            <span>
              <b>{user?.username || "同好"}</b>
              <small>知识工作台</small>
            </span>
          </Link>
        </div>
      </aside>
      {sidebarOpen ? (
        <button type="button" className="fan-sidebar-backdrop" aria-label="关闭导航" onClick={() => setSidebarOpen(false)} />
      ) : null}

      <section className="fan-shell">
        <header className="fan-mobile-head">
          <button type="button" className="fan-icon-control" aria-label="打开导航" onClick={() => setSidebarOpen(true)}>
            <Menu aria-hidden="true" />
          </button>
          <span>学园通信</span>
          <Link to="/chat" className="fan-icon-control" aria-label="返回工作台">
            <CircleUserRound aria-hidden="true" />
          </Link>
        </header>
        <section className="fan-conversation" aria-label="练习室对话">
          <header className="fan-conversation-head">
            <div>
              <p className="fan-eyebrow">COMMUNITY CHAT</p>
              <h1>练习室对话</h1>
            </div>
            <div className="fan-mode-toggle" role="group" aria-label="检索模式">
              <button type="button" className={executionMode === "RAG" ? "is-active" : ""} onClick={() => setExecutionMode("RAG")}>
                RAG
              </button>
              <button type="button" className={executionMode === "AGENT" ? "is-active" : ""} onClick={() => setExecutionMode("AGENT")}>
                Agent
              </button>
            </div>
          </header>
          <div className="fan-topic-strip">
            {topics.map((topic) => (
              <button type="button" key={topic} onClick={() => setTopic(topic)}>
                {topic}
              </button>
            ))}
          </div>
          <div className="fan-messages" ref={scrollRef} aria-live="polite">
            {!hasStarted && !hasConversation ? (
              <div className="fan-welcome">
                <img
                  src="/assets/gakuen/logo_main_logo_plain.png"
                  alt="学园偶像大师"
                  className="fan-welcome-logo"
                />
                <p className="fan-eyebrow">GAKUMAS COMMUNITY CHAT</p>
                <h2>欢迎来到练习室</h2>
                <p className="fan-welcome-desc">
                  从角色、P卡、活动到培养攻略，和偶像一起聊点什么吧。
                </p>
              </div>
            ) : null}
            {hasStarted || hasConversation ? (
              <article className="fan-message fan-message-welcome">
                <div className="fan-avatar fan-avatar-assistant">
                  <img src={assistantAvatar.src} alt={assistantAvatar.name} onError={handleAvatarError} />
                </div>
                <div className="fan-message-body">
                  <span className="fan-message-author">{assistantAvatar.name}</span>
                  <p>欢迎来到练习室。今天也一起为偶像加油吧。角色、P卡、活动和培养攻略，都可以问我。</p>
                </div>
              </article>
            ) : null}
            {visibleMessages.map((message) => (
              <article key={message.id} className={`fan-message ${message.role === "user" ? "fan-message-user" : ""}`}>
                <div className={`fan-avatar ${message.role === "assistant" ? "fan-avatar-assistant" : "fan-avatar-producer"}`}>
                  {message.role === "user" ? (
                    <img src="/assets/gakuen/logo_text_or.svg" alt="プロデューサー" onError={handleAvatarError} />
                  ) : (
                    <img src={assistantAvatar.src} alt={assistantAvatar.name} onError={handleAvatarError} />
                  )}
                </div>
                <div className="fan-message-body">
                  <span className="fan-message-author">{message.role === "user" ? "プロデューサー" : assistantAvatar.name}</span>
                  {message.role === "assistant" ? (
                    <div className="fan-markdown">
                      <MarkdownRenderer content={message.content || message.thinking || ""} />
                    </div>
                  ) : (
                    <p>{message.content || message.thinking}</p>
                  )}
                  {message.status === "error" ? <p className="fan-message-error">生成已中断，请重试或换一种问法。</p> : null}
                </div>
              </article>
            ))}
            {isStreaming ? (
              <div className="fan-typing">
                <i />
                <i />
                <i />
                <span>正在查阅资料</span>
              </div>
            ) : null}
          </div>
          <div className={`fan-composer ${hasStarted || hasConversation ? "is-docked" : "is-hero"}`}>
            <textarea
              ref={inputRef}
              value={value}
              onChange={(event) => setValue(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  void submit();
                }
              }}
              name="message" aria-label="输入消息" autoComplete="off" placeholder="问问练习室：角色、P卡、活动、培养攻略…"
              rows={1}
            />
            <div className="fan-composer-actions">
              <span className="fan-composer-hint">Enter 发送 · Shift+Enter 换行</span>
              <button
                type="button"
                className="fan-send"
                disabled={!value.trim() && !isStreaming}
                onClick={() => void submit()}
                aria-label={isStreaming ? "停止生成" : "发送消息"}
              >
                {isStreaming ? <X aria-hidden="true" /> : <ArrowUp aria-hidden="true" />}
              </button>
            </div>
          </div>
          <p className="fan-disclaimer">内容由知识库生成，请结合游戏内实际信息确认。</p>
        </section>
      </section>
    </main>
  );
}


