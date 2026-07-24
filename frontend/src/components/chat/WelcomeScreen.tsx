import * as React from "react";
import {
  ArrowUpRight,
  BookOpenCheck,
  BrainCircuit,
  FileSearch,
  Send,
  Sparkles,
  Square
} from "lucide-react";

import { cn } from "@/lib/utils";
import { listSampleQuestions } from "@/services/sampleQuestionService";
import { useChatStore } from "@/stores/chatStore";
import { ExecutionModeSelect } from "@/components/chat/ExecutionModeSelect";

type PromptPreset = {
  id?: string;
  title: string;
  description: string;
  prompt: string;
};

const DEFAULT_PRESETS: PromptPreset[] = [
  {
    title: "制度查询",
    description: "快速定位制度条款与适用范围",
    prompt: "请查询知识库中的相关制度，并说明适用范围和关键条款。"
  },
  {
    title: "文档总结",
    description: "提炼核心结论、风险与行动项",
    prompt: "请总结相关文档，并列出核心结论、潜在风险和后续行动项。"
  },
  {
    title: "知识对比",
    description: "综合多份资料识别差异和冲突",
    prompt: "请对比知识库中的相关资料，说明共同点、差异和需要确认的冲突。"
  }
];

export function WelcomeScreen() {
  const [value, setValue] = React.useState("");
  const [presets, setPresets] = React.useState(DEFAULT_PRESETS);
  const [isFocused, setIsFocused] = React.useState(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const isComposingRef = React.useRef(false);
  const {
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled
  } = useChatStore();

  React.useEffect(() => {
    let active = true;
    listSampleQuestions()
      .then((items) => {
        if (!active || !items?.length) return;
        const mapped = items
          .filter((item) => item.question?.trim())
          .slice(0, 3)
          .map((item, index) => {
            const question = item.question.trim();
            return {
              id: item.id,
              title: item.title?.trim() || `推荐问题 ${index + 1}`,
              description: item.description?.trim() || "来自管理员配置的常用问法",
              prompt: question
            };
          });
        if (mapped.length) setPresets(mapped);
      })
      .catch(() => null);
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    const element = textareaRef.current;
    if (!element) return;
    element.style.height = "auto";
    element.style.height = `${Math.min(element.scrollHeight, 180)}px`;
  }, [value]);

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      return;
    }
    const question = value.trim();
    if (!question) return;
    setValue("");
    await sendMessage(question);
  };

  const hasContent = value.trim().length > 0;

  return (
    <div className="relative flex h-full overflow-y-auto">
      <div className="mx-auto flex w-full max-w-[1040px] flex-col justify-center px-5 py-12 sm:px-8 lg:py-16">
        <div className="grid items-end gap-10 lg:grid-cols-[1fr_340px]">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full border border-teal-200 bg-teal-50 px-3 py-1.5 text-xs font-semibold text-teal-700">
              <Sparkles className="h-3.5 w-3.5" />
              企业知识助手
            </span>
            <h2 className="mt-6 max-w-2xl text-4xl font-semibold leading-[1.08] tracking-[-0.035em] text-slate-950 sm:text-5xl lg:text-[58px]">
              今天想从知识库
              <span className="block text-[#0f766e]">找到什么答案？</span>
            </h2>
            <p className="mt-5 max-w-xl text-sm leading-7 text-slate-500 sm:text-base">
              我会检索已授权的企业资料，综合多个来源生成答案，并保留可核验的检索链路。
            </p>
          </div>

          <div className="hidden rounded-2xl border border-slate-200 bg-white/70 p-5 shadow-sm lg:block">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-400">
                Retrieval pipeline
              </span>
              <span className="h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,.12)]" />
            </div>
            <div className="mt-5 space-y-4">
              {[
                ["01", "理解问题与业务意图"],
                ["02", "检索、去重并重排文档"],
                ["03", "生成带依据的回答"]
              ].map(([index, label]) => (
                <div key={index} className="flex items-center gap-3">
                  <span className="font-mono text-xs text-teal-700">{index}</span>
                  <span className="h-px flex-1 bg-slate-200" />
                  <span className="text-xs text-slate-600">{label}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-10">
          <div
            className={cn(
              "rounded-[22px] border bg-white p-2 shadow-[0_24px_55px_-34px_rgba(15,23,42,.55)] transition",
              isFocused ? "border-teal-600/50 ring-4 ring-teal-600/[0.07]" : "border-slate-200"
            )}
          >
            <textarea
              ref={textareaRef}
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder={
                deepThinkingEnabled
                  ? "描述一个需要跨文档深入分析的问题…"
                  : "例如：公司的远程办公制度有哪些要求？"
              }
              rows={1}
              className="max-h-[180px] min-h-[68px] w-full resize-none bg-transparent px-4 py-4 text-base leading-7 text-slate-800 outline-none placeholder:text-slate-400"
              onFocus={() => setIsFocused(true)}
              onBlur={() => setIsFocused(false)}
              onCompositionStart={() => {
                isComposingRef.current = true;
              }}
              onCompositionEnd={() => {
                isComposingRef.current = false;
              }}
              onKeyDown={(event) => {
                if (event.key !== "Enter" || event.shiftKey) return;
                const nativeEvent = event.nativeEvent as KeyboardEvent;
                if (
                  nativeEvent.isComposing ||
                  isComposingRef.current ||
                  nativeEvent.keyCode === 229
                ) {
                  return;
                }
                event.preventDefault();
                handleSubmit();
              }}
              aria-label="发送消息"
            />
            <div className="flex items-center gap-2 px-2 pb-2">
              <button
                type="button"
                onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                disabled={isStreaming}
                aria-pressed={deepThinkingEnabled}
                className={cn(
                  "inline-flex h-9 items-center gap-2 rounded-xl px-3 text-xs font-medium transition",
                  deepThinkingEnabled
                    ? "bg-teal-50 text-teal-700 ring-1 ring-inset ring-teal-200"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                )}
              >
                <BrainCircuit className="h-4 w-4" />
                深度思考
              </button>
              <ExecutionModeSelect />
              <span className="ml-auto hidden text-[11px] text-slate-400 sm:block">
                Enter 发送 · Shift + Enter 换行
              </span>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!hasContent && !isStreaming}
                aria-label={isStreaming ? "停止生成" : "发送消息"}
                className={cn(
                  "ml-1 flex h-10 w-10 items-center justify-center rounded-xl transition",
                  isStreaming
                    ? "bg-rose-100 text-rose-600"
                    : hasContent
                      ? "bg-[#0f766e] text-white hover:bg-[#115e59]"
                      : "cursor-not-allowed bg-slate-100 text-slate-300"
                )}
              >
                {isStreaming ? <Square className="h-3.5 w-3.5" /> : <Send className="h-4 w-4" />}
              </button>
            </div>
          </div>
        </div>

        <div className="mt-8">
          <div className="mb-3 flex items-center gap-2">
            <FileSearch className="h-4 w-4 text-slate-400" />
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-400">
              推荐问法
            </p>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            {presets.map((preset, index) => (
              <button
                key={preset.id || preset.title}
                type="button"
                disabled={isStreaming}
                onClick={() => {
                  setValue(preset.prompt);
                  textareaRef.current?.focus();
                }}
                className="group rounded-2xl border border-slate-200 bg-white/65 p-4 text-left transition hover:-translate-y-0.5 hover:border-teal-300 hover:bg-white hover:shadow-md disabled:cursor-not-allowed disabled:opacity-60"
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-900 text-xs font-semibold text-white">
                    0{index + 1}
                  </span>
                  <ArrowUpRight className="h-4 w-4 text-slate-300 transition group-hover:text-teal-600" />
                </div>
                <p className="mt-4 text-sm font-semibold text-slate-800">{preset.title}</p>
                <p className="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">
                  {preset.description}
                </p>
              </button>
            ))}
          </div>
        </div>

        <div className="mt-7 flex items-center justify-center gap-2 text-[11px] text-slate-400">
          <BookOpenCheck className="h-3.5 w-3.5" />
          回答基于当前可访问知识库，重要结论请核验引用原文
        </div>
      </div>
    </div>
  );
}
