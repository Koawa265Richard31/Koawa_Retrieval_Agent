import { api } from "@/services/api";

export async function stopTask(taskId: string) {
  return api.post<void>(`/rag/v3/stop?taskId=${encodeURIComponent(taskId)}`);
}

export interface FeedbackPayload {
  vote: number;
  reason?: string | null;
  comment?: string | null;
}

export async function submitFeedback(messageId: string, vote: number, reason?: string | null, comment?: string | null, rating?: number | null) {
  return api.post<void>(`/conversations/messages/${messageId}/feedback`, {
    vote,
    reason: reason || undefined,
    comment: comment || undefined,
    rating: rating ?? undefined
  });
}

