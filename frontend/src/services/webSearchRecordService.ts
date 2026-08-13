import { api } from "@/services/api";

export interface WebSearchRecord {
  id: string;
  traceId?: string | null;
  conversationId?: string | null;
  messageId?: string | null;
  question?: string | null;
  provider?: string | null;
  query?: string | null;
  url?: string | null;
  urlTitle?: string | null;
  description?: string | null;
  snippet?: string | null;
  visitTime?: string | null;
  resourceCreateTime?: string | null;
  createTime?: string | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface WebSearchRecordPageParams {
  current?: number;
  size?: number;
  provider?: string | null;
  keyword?: string;
}

export async function getWebSearchRecords(
  params: WebSearchRecordPageParams = {}
): Promise<PageResult<WebSearchRecord>> {
  return api.get<PageResult<WebSearchRecord>, PageResult<WebSearchRecord>>(
    "/web-search-records",
    {
      params: {
        current: params.current || 1,
        size: params.size || 10,
        provider: params.provider === "all" ? undefined : params.provider || undefined,
        keyword: params.keyword || undefined
      }
    }
  );
}