import * as React from "react";
import { differenceInCalendarDays, isValid } from "date-fns";
import {
  Database,
  LogOut,
  MoreHorizontal,
  Pencil,
  Plus,
  Search,
  Settings,
  Trash2,
  X
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

export function Sidebar({ isOpen, onClose }: SidebarProps) {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const {
    sessions,
    currentSessionId,
    sessionsLoaded,
    createSession,
    deleteSession,
    renameSession,
    fetchSessions
  } = useChatStore();
  const [query, setQuery] = React.useState("");
  const [renamingId, setRenamingId] = React.useState<string | null>(null);
  const [renameValue, setRenameValue] = React.useState("");
  const [deleteTarget, setDeleteTarget] = React.useState<{ id: string; title: string } | null>(
    null
  );
  const [avatarFailed, setAvatarFailed] = React.useState(false);
  const renameInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    if (!sessionsLoaded) {
      fetchSessions().catch(() => null);
    }
  }, [fetchSessions, sessionsLoaded]);

  React.useEffect(() => {
    if (renamingId) {
      renameInputRef.current?.focus();
      renameInputRef.current?.select();
    }
  }, [renamingId]);

  React.useEffect(() => {
    setAvatarFailed(false);
  }, [user?.avatar, user?.userId]);

  const filteredSessions = React.useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return sessions;
    return sessions.filter((session) =>
      `${session.title || "新对话"} ${session.id}`.toLowerCase().includes(keyword)
    );
  }, [query, sessions]);

  const groupedSessions = React.useMemo(() => {
    const now = new Date();
    const labels = ["今天", "7 天内", "30 天内", "更早"];
    const groups = new Map(labels.map((label) => [label, [] as typeof filteredSessions]));
    filteredSessions.forEach((session) => {
      const parsed = session.lastTime ? new Date(session.lastTime) : now;
      const date = isValid(parsed) ? parsed : now;
      const days = Math.max(0, differenceInCalendarDays(now, date));
      const label =
        days === 0 ? labels[0] : days <= 7 ? labels[1] : days <= 30 ? labels[2] : labels[3];
      groups.get(label)?.push(session);
    });
    return labels
      .map((label) => ({ label, items: groups.get(label) || [] }))
      .filter((group) => group.items.length > 0);
  }, [filteredSessions]);

  const createNewSession = async () => {
    await createSession();
    navigate("/chat");
    onClose();
  };

  const openSession = (id: string) => {
    if (renamingId === id) return;
    navigate(`/chat/${id}`);
    onClose();
  };

  const startRename = (id: string, title: string) => {
    setRenamingId(id);
    setRenameValue(title || "新对话");
  };

  const cancelRename = () => {
    setRenamingId(null);
    setRenameValue("");
  };

  const commitRename = async () => {
    if (!renamingId) return;
    const title = renameValue.trim();
    const original = sessions.find((session) => session.id === renamingId)?.title || "新对话";
    if (title && title !== original) {
      await renameSession(renamingId, title);
    }
    cancelRename();
  };

  const avatarUrl = user?.avatar?.trim();
  const showAvatar = Boolean(avatarUrl) && !avatarFailed;
  const displayName = user?.username || user?.userId || "用户";
  const avatarFallback = displayName.slice(0, 1).toUpperCase();

  return (
    <>
      <button
        type="button"
        aria-label="关闭会话列表"
        className={cn(
          "fixed inset-0 z-30 bg-slate-950/45 backdrop-blur-sm transition-opacity lg:hidden",
          isOpen ? "opacity-100" : "pointer-events-none opacity-0"
        )}
        onClick={onClose}
      />
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-[292px] flex-col bg-[#0d1b2a] text-slate-200 shadow-2xl transition-transform lg:static lg:z-auto lg:m-3 lg:mr-0 lg:h-[calc(100vh-24px)] lg:translate-x-0 lg:rounded-[22px] lg:shadow-[0_24px_60px_-36px_rgba(2,6,23,.9)]",
          isOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex items-center gap-3 px-5 pb-5 pt-5">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl border border-teal-300/20 bg-teal-300/10 text-teal-200">
            <Database className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold tracking-[0.06em] text-white">
              KOAWA KNOWLEDGE
            </p>
            <p className="text-[11px] text-slate-400">企业知识智能平台</p>
          </div>
          <button
            type="button"
            aria-label="关闭侧边栏"
            className="rounded-lg p-2 text-slate-400 hover:bg-white/10 hover:text-white lg:hidden"
            onClick={onClose}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="px-4">
          <button
            type="button"
            className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-teal-300 text-sm font-semibold text-[#0d1b2a] shadow-[0_12px_28px_-14px_rgba(94,234,212,.8)] transition hover:bg-teal-200"
            onClick={() => createNewSession().catch(() => null)}
          >
            <Plus className="h-4 w-4" />
            发起新对话
          </button>

          <div className="relative mt-4">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索历史对话"
              className="h-10 w-full rounded-xl border border-white/10 bg-white/[0.055] pl-9 pr-3 text-sm text-white outline-none placeholder:text-slate-500 focus:border-teal-300/40 focus:bg-white/[0.08]"
            />
          </div>
        </div>

        <div className="mt-6 flex min-h-0 flex-1 flex-col">
          <div className="flex items-center justify-between px-5">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
              历史对话
            </p>
            <span className="text-[11px] tabular-nums text-slate-600">{sessions.length}</span>
          </div>

          <div className="sidebar-scroll mt-3 min-h-0 flex-1 overflow-y-auto px-3 pb-4">
            {!sessionsLoaded ? (
              <p className="px-2 py-6 text-center text-xs text-slate-500">正在加载会话…</p>
            ) : groupedSessions.length === 0 ? (
              <div className="rounded-xl border border-dashed border-white/10 px-4 py-8 text-center">
                <p className="text-sm text-slate-400">暂无匹配的对话</p>
                <p className="mt-1 text-xs text-slate-600">从一个业务问题开始吧</p>
              </div>
            ) : (
              groupedSessions.map((group) => (
                <section key={group.label} className="mb-5">
                  <p className="mb-1.5 px-2 text-[11px] text-slate-500">{group.label}</p>
                  <div className="space-y-1">
                    {group.items.map((session) => (
                      <div
                        key={session.id}
                        role="button"
                        tabIndex={0}
                        onClick={() => openSession(session.id)}
                        onKeyDown={(event) => {
                          if (event.key === "Enter") openSession(session.id);
                        }}
                        className={cn(
                          "group flex min-h-10 cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm transition",
                          currentSessionId === session.id
                            ? "bg-white/10 text-white"
                            : "text-slate-400 hover:bg-white/[0.055] hover:text-slate-200"
                        )}
                      >
                        <span
                          className={cn(
                            "h-1.5 w-1.5 shrink-0 rounded-full",
                            currentSessionId === session.id ? "bg-teal-300" : "bg-slate-700"
                          )}
                        />
                        {renamingId === session.id ? (
                          <input
                            ref={renameInputRef}
                            value={renameValue}
                            onChange={(event) => setRenameValue(event.target.value)}
                            onClick={(event) => event.stopPropagation()}
                            onBlur={() => commitRename().catch(() => null)}
                            onKeyDown={(event) => {
                              event.stopPropagation();
                              if (event.key === "Enter") commitRename().catch(() => null);
                              if (event.key === "Escape") cancelRename();
                            }}
                            className="h-7 min-w-0 flex-1 rounded-md border border-teal-300/30 bg-slate-950/40 px-2 text-sm text-white outline-none"
                          />
                        ) : (
                          <span className="min-w-0 flex-1 truncate">
                            {session.title || "新对话"}
                          </span>
                        )}
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button
                              type="button"
                              aria-label="会话操作"
                              onClick={(event) => event.stopPropagation()}
                              className="rounded-md p-1 text-slate-500 opacity-0 transition hover:bg-white/10 hover:text-white group-hover:opacity-100 data-[state=open]:opacity-100"
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="start" className="w-36">
                            <DropdownMenuItem
                              onClick={(event) => {
                                event.stopPropagation();
                                startRename(session.id, session.title || "新对话");
                              }}
                            >
                              <Pencil className="mr-2 h-4 w-4" />
                              重命名
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              className="text-rose-600 focus:text-rose-600"
                              onClick={(event) => {
                                event.stopPropagation();
                                setDeleteTarget({
                                  id: session.id,
                                  title: session.title || "新对话"
                                });
                              }}
                            >
                              <Trash2 className="mr-2 h-4 w-4" />
                              删除
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    ))}
                  </div>
                </section>
              ))
            )}
          </div>
        </div>

        <div className="border-t border-white/10 p-3">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                type="button"
                className="flex w-full items-center gap-3 rounded-xl p-2 text-left transition hover:bg-white/[0.06] data-[state=open]:bg-white/[0.08]"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-teal-300 text-sm font-semibold text-[#0d1b2a]">
                  {showAvatar ? (
                    <img
                      src={avatarUrl}
                      alt={displayName}
                      className="h-full w-full object-cover"
                      onError={() => setAvatarFailed(true)}
                    />
                  ) : (
                    avatarFallback
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-white">
                    {displayName}
                  </span>
                  <span className="block text-[11px] text-slate-500">
                    {user?.role === "admin" ? "系统管理员" : "知识库成员"}
                  </span>
                </span>
                <MoreHorizontal className="h-4 w-4 text-slate-500" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" side="top" sideOffset={8} className="w-52">
              {user?.role === "admin" ? (
                <DropdownMenuItem onClick={() => navigate("/admin")}>
                  <Settings className="mr-2 h-4 w-4" />
                  管理控制台
                </DropdownMenuItem>
              ) : null}
              <DropdownMenuItem
                className="text-rose-600 focus:text-rose-600"
                onClick={() => logout()}
              >
                <LogOut className="mr-2 h-4 w-4" />
                退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>

      <AlertDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除该会话？</AlertDialogTitle>
            <AlertDialogDescription>
              “{deleteTarget?.title || "该会话"}”及其消息记录将被永久删除。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (!deleteTarget) return;
                const target = deleteTarget;
                const isCurrent = currentSessionId === target.id;
                setDeleteTarget(null);
                deleteSession(target.id)
                  .then(() => {
                    if (isCurrent) navigate("/chat");
                  })
                  .catch(() => null);
              }}
            >
              确认删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
