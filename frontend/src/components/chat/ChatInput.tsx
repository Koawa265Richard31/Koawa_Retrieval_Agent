import * as React from "react";
import { BrainCircuit, CornerDownLeft, Send, Square } from "lucide-react";

import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import { ExecutionModeSelect } from "@/components/chat/ExecutionModeSelect";

export function ChatInput() {
  const [value, setValue] = React.useState("");
  const [isFocused, setIsFocused] = React.useState(false);
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const {
    sendMessage,
    isStreaming,
    cancelGeneration,
    deepThinkingEnabled,
    setDeepThinkingEnabled,
    inputFocusKey
  } = useChatStore();

  const focusInput = React.useCallback(() => {
    textareaRef.current?.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const element = textareaRef.current;
    if (!element) return;
    element.style.height = "auto";
    element.style.height = `${Math.min(element.scrollHeight, 180)}px`;
  }, []);

  React.useEffect(adjustHeight, [value, adjustHeight]);
  React.useEffect(() => {
    if (inputFocusKey) focusInput();
  }, [inputFocusKey, focusInput]);

  const handleSubmit = async () => {
    if (isStreaming) {
      cancelGeneration();
      focusInput();
      return;
    }
    const question = value.trim();
    if (!question) return;
    setValue("");
    await sendMessage(question);
    focusInput();
  };

  const hasContent = value.trim().length > 0;

  return (
    <div>
      <div
        className={cn(
          "rounded-2xl border bg-white p-2 shadow-[0_12px_32px_-20px_rgba(15,23,42,.45)] transition",
          isFocused
            ? "border-teal-600/50 ring-4 ring-teal-600/[0.07]"
            : "border-slate-200 hover:border-slate-300"
        )}
      >
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={deepThinkingEnabled ? "描述需要深入分析的业务问题…" : "询问企业知识库…"}
          rows={1}
          className="max-h-[180px] min-h-[54px] w-full resize-none bg-transparent px-3 py-3 text-[15px] leading-6 text-slate-800 outline-none placeholder:text-slate-400"
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
            if (nativeEvent.isComposing || isComposingRef.current || nativeEvent.keyCode === 229)
              return;
            event.preventDefault();
            handleSubmit();
          }}
          aria-label="聊天输入框"
        />

        <div className="flex items-center gap-2 px-1 pb-1">
          <button
            type="button"
            onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
            disabled={isStreaming}
            aria-pressed={deepThinkingEnabled}
            className={cn(
              "inline-flex h-9 items-center gap-2 rounded-xl px-3 text-xs font-medium transition",
              deepThinkingEnabled
                ? "bg-teal-50 text-teal-700 ring-1 ring-inset ring-teal-200"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200",
              isStreaming && "cursor-not-allowed opacity-60"
            )}
          >
            <BrainCircuit className="h-4 w-4" />
            深度思考
            {deepThinkingEnabled ? <span className="h-1.5 w-1.5 rounded-full bg-teal-500" /> : null}
          </button>

          <ExecutionModeSelect />

          <span className="ml-auto hidden items-center gap-1.5 text-[11px] text-slate-400 sm:inline-flex">
            <CornerDownLeft className="h-3.5 w-3.5" />
            Enter 发送 · Shift + Enter 换行
          </span>

          <button
            type="button"
            onClick={handleSubmit}
            disabled={!hasContent && !isStreaming}
            aria-label={isStreaming ? "停止生成" : "发送消息"}
            className={cn(
              "ml-1 flex h-9 w-9 items-center justify-center rounded-xl transition",
              isStreaming
                ? "bg-rose-100 text-rose-600 hover:bg-rose-200"
                : hasContent
                  ? "bg-[#0f766e] text-white shadow-sm hover:bg-[#115e59]"
                  : "cursor-not-allowed bg-slate-100 text-slate-300"
            )}
          >
            {isStreaming ? <Square className="h-3.5 w-3.5" /> : <Send className="h-4 w-4" />}
          </button>
        </div>
      </div>
      <p className="mt-2 text-center text-[11px] text-slate-400">
        AI 生成内容仅供参考，关键结论请结合引用文档核验
      </p>
    </div>
  );
}
