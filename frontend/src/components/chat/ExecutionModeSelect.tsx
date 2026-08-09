import * as React from "react";
import { Route } from "lucide-react";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";
import { useChatStore } from "@/stores/chatStore";
import type { ChatExecutionMode } from "@/types";

const MODE_LABELS: Record<ChatExecutionMode, string> = {
  AUTO: "自动路由",
  RAG: "RAG",
  AGENT: "Agent Loop",
  AGENTIC: "Agentic Retrieval"
};

export function ExecutionModeSelect() {
  const executionMode = useChatStore((state) => state.executionMode);
  const setExecutionMode = useChatStore((state) => state.setExecutionMode);
  const isStreaming = useChatStore((state) => state.isStreaming);

  return (
    <div className="flex items-center gap-1.5">
      <Route className="h-4 w-4 text-amber-600" aria-hidden="true" />
      <Select
        value={executionMode}
        onValueChange={(value) => setExecutionMode(value as ChatExecutionMode)}
        disabled={isStreaming}
      >
        <SelectTrigger
          className="h-9 w-[164px] rounded-xl border-amber-200 bg-amber-50 px-2.5 text-xs font-medium text-amber-800 focus:ring-amber-500"
          aria-label="执行模式"
        >
          <SelectValue>{MODE_LABELS[executionMode]}</SelectValue>
        </SelectTrigger>
        <SelectContent>
          {(Object.entries(MODE_LABELS) as [ChatExecutionMode, string][]).map(
            ([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
              </SelectItem>
            )
          )}
        </SelectContent>
      </Select>
    </div>
  );
}
