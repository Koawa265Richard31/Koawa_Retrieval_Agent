import * as React from "react";
import {
  ArrowRight,
  BookOpenCheck,
  Database,
  Eye,
  EyeOff,
  Lock,
  Search,
  ShieldCheck,
  Sparkles,
  User
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { useAuthStore } from "@/stores/authStore";

const capabilities = [
  {
    icon: Search,
    title: "多路知识检索",
    description: "融合向量检索、意图路由与精排结果"
  },
  {
    icon: BookOpenCheck,
    title: "答案可追溯",
    description: "保留知识来源与完整检索链路"
  },
  {
    icon: ShieldCheck,
    title: "企业级权限",
    description: "面向组织、知识库和文档的访问控制"
  }
];

function AuthPage({ mode }: { mode: "login" | "register" }) {
  const navigate = useNavigate();
  const { login, register, isLoading } = useAuthStore();
  const isRegister = mode === "register";
  const [showPassword, setShowPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [form, setForm] = React.useState({
    username: isRegister ? "" : "admin",
    password: "",
    confirmPassword: ""
  });
  const [inviteCode, setInviteCode] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!form.username.trim() || !form.password) {
      setError("请输入用户名和密码。");
      return;
    }
    if (!isRegister && inviteCode.trim().toUpperCase() !== "GAKUMAS-FAN-2026") {
      setError("邀请码无效，请确认后再试。");
      return;
    }
    if (isRegister && form.password !== form.confirmPassword) {
      setError("两次输入的密码不一致。");
      return;
    }
    try {
      if (isRegister) {
        await register(form.username.trim(), form.password, form.confirmPassword);
      } else {
        await login(form.username.trim(), form.password);
      }
      if (!isRegister && !remember) {
        // 预留仅会话级登录态能力。
      }
      navigate(isRegister ? "/chat" : "/fan");
    } catch (err) {
      setError((err as Error).message || `${isRegister ? "注册" : "登录"}失败，请稍后重试。`);
    }
  };

  return (
    <main className="relative min-h-screen overflow-hidden bg-[#0d1b2a] text-slate-100">
      <div
        aria-hidden="true"
        className="absolute inset-0 opacity-20 [background-image:linear-gradient(rgba(255,255,255,.08)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.08)_1px,transparent_1px)] [background-size:56px_56px]"
      />
      <div
        aria-hidden="true"
        className="absolute -left-32 top-1/3 h-[420px] w-[420px] rounded-full bg-teal-500/10 blur-[110px]"
      />
      <div
        aria-hidden="true"
        className="absolute -right-40 -top-40 h-[520px] w-[520px] rounded-full bg-amber-300/10 blur-[130px]"
      />

      <div className="relative mx-auto grid min-h-screen w-full max-w-[1440px] lg:grid-cols-[1.1fr_0.9fr]">
        <section className="hidden flex-col justify-between px-6 py-8 sm:px-10 lg:flex lg:px-16 lg:py-12">
          <div className="flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl border border-teal-300/20 bg-teal-300/10 text-teal-200">
              <Database className="h-5 w-5" />
            </span>
            <div>
              <p className="text-sm font-semibold tracking-[0.08em] text-white">KOAWA KNOWLEDGE</p>
              <p className="text-xs text-slate-400">企业知识智能平台</p>
            </div>
          </div>

          <div className="max-w-2xl py-16 lg:py-10">
            <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-teal-200">
              <Sparkles className="h-3.5 w-3.5" />
              让组织知识真正参与决策
            </span>
            <h1 className="mt-7 max-w-xl text-4xl font-semibold leading-[1.08] tracking-[-0.035em] text-white sm:text-5xl lg:text-[64px]">
              从海量文档中，
              <span className="block text-teal-200">找到可信答案。</span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-slate-300 sm:text-lg">
              将分散的制度、手册与业务资料统一沉淀，通过可追溯的 RAG
              流水线，为团队提供准确、及时且有依据的回答。
            </p>

            <div className="mt-10 grid max-w-2xl gap-4 sm:grid-cols-3">
              {capabilities.map((item) => {
                const Icon = item.icon;
                return (
                  <div
                    key={item.title}
                    className="rounded-2xl border border-white/10 bg-white/[0.045] p-4 backdrop-blur"
                  >
                    <Icon className="h-5 w-5 text-teal-200" />
                    <p className="mt-4 text-sm font-semibold text-white">{item.title}</p>
                    <p className="mt-1 text-xs leading-5 text-slate-400">{item.description}</p>
                  </div>
                );
              })}
            </div>
          </div>

          <p className="text-xs text-slate-500">Koawa Knowledge · Private deployment ready</p>
        </section>

        <section className="flex items-center justify-center border-t border-white/10 bg-[#f5f2eb] px-5 py-12 text-slate-900 lg:border-l lg:border-t-0">
          <div className="w-full max-w-[430px]">
            <div className="mb-10 flex items-center gap-3 lg:hidden">
              <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#0d1b2a] text-teal-200">
                <Database className="h-5 w-5" />
              </span>
              <div>
                <p className="text-sm font-semibold tracking-[0.06em] text-slate-950">
                  KOAWA KNOWLEDGE
                </p>
                <p className="text-xs text-slate-500">企业知识智能平台</p>
              </div>
            </div>
            <div className="mb-9">
              <p className="text-xs font-semibold uppercase tracking-[0.22em] text-teal-700">
                Secure workspace
              </p>
              <h2 className="mt-3 text-3xl font-semibold tracking-[-0.025em] text-slate-950">
                {isRegister ? "创建个人账号" : "登录知识工作台"}
              </h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                {isRegister
                  ? "注册后将以普通成员身份进入知识工作台。"
                  : "使用你的企业账号继续访问。登录完成后将进入已授权的交流空间。"}
              </p>
            </div>

            <form className="space-y-5" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <label htmlFor="username" className="text-sm font-medium text-slate-700">
                  用户名
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="username"
                    placeholder="请输入用户名"
                    value={form.username}
                    onChange={(event) =>
                      setForm((prev) => ({ ...prev, username: event.target.value }))
                    }
                    className="h-12 rounded-xl border-slate-300 bg-white pl-11 shadow-sm focus-visible:ring-teal-700"
                    autoComplete="username"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <label htmlFor="password" className="text-sm font-medium text-slate-700">
                  密码
                </label>
                <div className="relative">
                  <Lock className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="请输入密码"
                    value={form.password}
                    onChange={(event) =>
                      setForm((prev) => ({ ...prev, password: event.target.value }))
                    }
                    className="h-12 rounded-xl border-slate-300 bg-white pl-11 pr-11 shadow-sm focus-visible:ring-teal-700"
                    autoComplete={isRegister ? "new-password" : "current-password"}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
                    aria-label={showPassword ? "隐藏密码" : "显示密码"}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {!isRegister ? (
                <div className="space-y-2">
                  <label htmlFor="inviteCode" className="text-sm font-medium text-slate-700">
                    同好邀请码
                  </label>
                  <Input
                    id="inviteCode"
                    value={inviteCode}
                    onChange={(event) => setInviteCode(event.target.value)}
                    placeholder="请输入邀请码"
                    className="h-12 rounded-xl border-slate-300 bg-white font-mono uppercase tracking-[0.12em] shadow-sm focus-visible:ring-teal-700"
                    autoComplete="off"
                  />
                </div>
              ) : null}

              {isRegister ? (
                <div className="space-y-2">
                  <label htmlFor="confirmPassword" className="text-sm font-medium text-slate-700">
                    确认密码
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    <Input
                      id="confirmPassword"
                      type={showPassword ? "text" : "password"}
                      placeholder="请再次输入密码"
                      value={form.confirmPassword}
                      onChange={(event) =>
                        setForm((prev) => ({ ...prev, confirmPassword: event.target.value }))
                      }
                      className="h-12 rounded-xl border-slate-300 bg-white pl-11 shadow-sm focus-visible:ring-teal-700"
                      autoComplete="new-password"
                    />
                  </div>
                  <p className="text-xs text-slate-400">
                    用户名使用 3-32 位字母、数字、下划线或连字符；密码至少 8 位。
                  </p>
                </div>
              ) : (
                <div className="flex items-center justify-between text-sm">
                  <label className="flex items-center gap-2 text-slate-600">
                    <Checkbox
                      checked={remember}
                      onCheckedChange={(value) => setRemember(Boolean(value))}
                    />
                    保持登录
                  </label>
                  <button
                    type="button"
                    onClick={() => navigate("/register")}
                    className="text-xs font-medium text-teal-700 hover:text-teal-900"
                  >
                    创建账号
                  </button>
                </div>
              )}

              {error ? (
                <p
                  role="alert"
                  className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
                >
                  {error}
                </p>
              ) : null}

              <Button
                type="submit"
                className="h-12 w-full rounded-xl bg-[#0f766e] text-white shadow-[0_12px_28px_-12px_rgba(15,118,110,.8)] hover:bg-[#115e59]"
                disabled={isLoading}
              >
                {isLoading
                  ? isRegister
                    ? "正在创建..."
                    : "正在验证..."
                  : isRegister
                    ? "创建并进入工作台"
                    : "登录并继续"}
                {!isLoading ? <ArrowRight className="ml-2 h-4 w-4" /> : null}
              </Button>
            </form>

            <p className="mt-7 text-center text-xs leading-5 text-slate-400">
              {isRegister ? (
                <>
                  已有账号？{" "}
                  <button
                    type="button"
                    onClick={() => navigate("/login")}
                    className="font-medium text-teal-700 hover:text-teal-900"
                  >
                    返回登录
                  </button>
                </>
              ) : (
                "登录即表示你同意遵守企业数据安全与知识访问规范"
              )}
            </p>
          </div>
        </section>
      </div>
    </main>
  );
}

export function LoginPage() {
  return <AuthPage mode="login" />;
}

export function RegisterPage() {
  return <AuthPage mode="register" />;
}
