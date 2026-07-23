import { Menu, PanelTop, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

interface HeaderProps {
  onToggleSidebar: () => void;
}

export function Header({ onToggleSidebar }: HeaderProps) {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const { currentSessionId, sessions, isStreaming } = useChatStore();
  const currentSession = sessions.find((session) => session.id === currentSessionId);

  return (
    <header className="relative z-20 border-b border-slate-200/70 bg-[#fbfaf7]/90 backdrop-blur-xl">
      <div className="flex h-[68px] items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            aria-label="打开会话列表"
            className="shrink-0 rounded-xl text-slate-600 hover:bg-slate-200/60 lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="truncate text-[15px] font-semibold tracking-[-0.01em] text-slate-900">
                {currentSession?.title || "新对话"}
              </h1>
              {isStreaming ? (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-50 px-2 py-0.5 text-[10px] font-semibold text-teal-700">
                  <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-teal-500" />
                  生成中
                </span>
              ) : null}
            </div>
            <p className="mt-0.5 hidden text-xs text-slate-500 sm:block">
              基于企业知识库生成答案，请核对引用来源
            </p>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <span className="hidden items-center gap-1.5 rounded-full border border-emerald-200/80 bg-emerald-50/80 px-3 py-1.5 text-xs font-medium text-emerald-700 md:inline-flex">
            <ShieldCheck className="h-3.5 w-3.5" />
            知识库已连接
          </span>
          {user?.role === "admin" ? (
            <Button
              variant="outline"
              size="sm"
              className="h-9 rounded-xl border-slate-200 bg-white/80 px-3 text-slate-700 shadow-sm hover:bg-white"
              onClick={() => navigate("/admin")}
            >
              <PanelTop className="mr-2 h-4 w-4" />
              <span className="hidden sm:inline">管理控制台</span>
              <span className="sm:hidden">管理</span>
            </Button>
          ) : null}
        </div>
      </div>
    </header>
  );
}
