import { api } from "@/services/api";

export interface MessageFeedback {
  id: string;
  messageId: string;
  conversationId: string;
  userId: string;
  username?: string | null;
  vote: number;
  reason?: string | null;
  comment?: string | null;
  question?: string | null;
  answer?: string | null;
  handled: number;
  handleNote?: string | null;
  handleTime?: string | null;
  handlerId?: string | null;
  handlerName?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface FeedbackStats {
  total: number;
  likeCount: number;
  dislikeCount: number;
  unhandledCount: number;
  handledCount: number;
  todayCount: number;
}

export interface FeedbackPageParams {
  current?: number;
  size?: number;
  vote?: number | null;
  handled?: number | null;
  keyword?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export async function getFeedbackPage(params: FeedbackPageParams = {}): Promise<PageResult<MessageFeedback>> {
  return api.get<PageResult<MessageFeedback>, PageResult<MessageFeedback>>("/message-feedback", {
    params: {
      current: params.current || 1,
      size: params.size || 10,
      vote: params.vote ?? undefined,
      handled: params.handled ?? undefined,
      keyword: params.keyword || undefined
    }
  });
}

export async function getFeedbackStats(): Promise<FeedbackStats> {
  return api.get<FeedbackStats, FeedbackStats>("/message-feedback/stats");
}

export async function handleFeedback(id: string, note?: string): Promise<void> {
  await api.put(`/message-feedback/${id}/handle`, { note: note || undefined });
}

export async function unhandleFeedback(id: string): Promise<void> {
  await api.put(`/message-feedback/${id}/unhandle`);
}
