import { useCallback, useEffect, useState } from "react";
import { ExternalLink, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import { RelativeTime } from "@/components/RelativeTime";
import {
  getWebSearchRecords,
  type PageResult,
  type WebSearchRecord
} from "@/services/webSearchRecordService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

const PROVIDER_LABELS: Record<string, string> = {
  bocha: "博查",
  bing: "Bing"
};

function formatTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function ProviderBadge({ provider }: { provider?: string | null }) {
  if (!provider) {
    return <Badge variant="outline" className="admin-status-muted">未知</Badge>;
  }
  const label = PROVIDER_LABELS[provider] || provider;
  const isBocha = provider === "bocha";
  return (
    <Badge
      variant="outline"
      className={isBocha ? "border-sky-200 bg-sky-50 text-sky-600" : "border-emerald-200 bg-emerald-50 text-emerald-600"}
    >
      {label}
    </Badge>
  );
}

export function WebSearchRecordsPage() {
  const [pageData, setPageData] = useState<PageResult<WebSearchRecord> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [keyword, setKeyword] = useState("");
  const [providerFilter, setProviderFilter] = useState<string>("all");
  const [detail, setDetail] = useState<WebSearchRecord | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const data = await getWebSearchRecords({
        current: pageNo,
        size: PAGE_SIZE,
        provider: providerFilter,
        keyword: keyword || undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载联网检索记录失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [pageNo, keyword, providerFilter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSearch = () => {
    setKeyword(searchKeyword.trim());
    setPageNo(1);
  };

  const handleProviderChange = (value: string) => {
    setProviderFilter(value);
    setPageNo(1);
  };

  const handleRefresh = () => {
    loadData();
  };

  const openDetail = (item: WebSearchRecord) => {
    setDetail(item);
    setDetailOpen(true);
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">联网检索记录</h1>
          <p className="admin-page-subtitle">Agent 联网搜索访问记录，网址与原始问题绑定，可审计搜索来源</p>
        </div>
        <div className="admin-page-actions">
          <Select value={providerFilter} onValueChange={handleProviderChange}>
            <SelectTrigger className="w-[140px]">
              <SelectValue placeholder="搜索来源" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部来源</SelectItem>
              <SelectItem value="bocha">博查</SelectItem>
              <SelectItem value="bing">Bing</SelectItem>
            </SelectContent>
          </Select>
          <Input
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") handleSearch();
            }}
            placeholder="搜索问题/查询/网址/标题"
            className="w-[260px]"
          />
          <Button variant="outline" onClick={handleSearch}>
            <Search className="mr-2 h-4 w-4" />
            搜索
          </Button>
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading && !pageData ? (
            <div className="py-16 text-center text-sm text-slate-500">加载中…</div>
          ) : pageData && pageData.records.length === 0 ? (
            <div className="py-16 text-center text-sm text-slate-400">
              暂无联网检索记录，Agent 触发联网搜索后自动记录
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[150px]">记录时间</TableHead>
                      <TableHead className="min-w-[180px]">原始问题</TableHead>
                      <TableHead className="min-w-[200px]">搜索查询</TableHead>
                      <TableHead className="w-[90px]">来源</TableHead>
                      <TableHead className="min-w-[220px]">网址</TableHead>
                      <TableHead className="min-w-[160px]">网址标题</TableHead>
                      <TableHead className="w-[140px]">访问时间</TableHead>
                      <TableHead className="w-[150px]">资源创建时间</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pageData?.records.map((item) => (
                      <TableRow
                        key={item.id}
                        className="cursor-pointer"
                        onClick={() => openDetail(item)}
                      >
                        <TableCell className="whitespace-nowrap">
                          <RelativeTime value={item.createTime} />
                        </TableCell>
                        <TableCell>
                          <span className="line-clamp-2 text-slate-800">{item.question || "-"}</span>
                        </TableCell>
                        <TableCell>
                          <span className="line-clamp-2 text-slate-600">{item.query || "-"}</span>
                        </TableCell>
                        <TableCell>
                          <ProviderBadge provider={item.provider} />
                        </TableCell>
                        <TableCell>
                          {item.url ? (
                            <a
                              href={item.url}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex max-w-[280px] items-center gap-1 text-sky-600 hover:text-sky-700 hover:underline"
                              onClick={(event) => event.stopPropagation()}
                            >
                              <ExternalLink className="h-3.5 w-3.5 shrink-0" />
                              <span className="truncate">{item.url}</span>
                            </a>
                          ) : (
                            "-"
                          )}
                        </TableCell>
                        <TableCell>
                          <span className="line-clamp-2 text-slate-600">{item.urlTitle || "-"}</span>
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-slate-500">
                          {formatTime(item.visitTime)}
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-slate-500">
                          {formatTime(item.resourceCreateTime)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <span className="text-sm text-slate-500">
                  共 {pageData?.total ?? 0} 条记录
                </span>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
                    disabled={!pageData || pageData.current <= 1}
                  >
                    上一页
                  </Button>
                  <span className="text-sm text-slate-600">
                    {pageData?.current ?? 1} / {pageData?.pages ?? 1}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPageNo((prev) => Math.min(pageData?.pages || 1, prev + 1))}
                    disabled={!pageData || pageData.current >= pageData.pages}
                  >
                    下一页
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="sm:max-w-[680px]">
          <DialogHeader>
            <DialogTitle>联网检索详情</DialogTitle>
            <DialogDescription>
              {detail ? (
                <span className="flex items-center gap-2">
                  <ProviderBadge provider={detail.provider} />
                  <span className="text-slate-400">记录时间：{formatTime(detail.createTime)}</span>
                </span>
              ) : null}
            </DialogDescription>
          </DialogHeader>
          {detail ? (
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-slate-700">原始问题</label>
                <div className="mt-1 max-h-24 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
                  {detail.question || "-"}
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">搜索查询</label>
                <div className="mt-1 whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
                  {detail.query || "-"}
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">网址</label>
                <div className="mt-1 flex items-start gap-2 rounded-lg bg-slate-50 p-3">
                  {detail.url ? (
                    <a
                      href={detail.url}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 break-all text-sky-600 hover:underline"
                    >
                      <ExternalLink className="h-3.5 w-3.5 shrink-0" />
                      {detail.url}
                    </a>
                  ) : (
                    <span className="text-sm text-slate-600">-</span>
                  )}
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="text-sm font-medium text-slate-700">网址标题</label>
                  <div className="mt-1 text-sm text-slate-600">{detail.urlTitle || "-"}</div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">访问时间 / 资源创建时间</label>
                  <div className="mt-1 text-sm text-slate-600">
                    {formatTime(detail.visitTime)} / {formatTime(detail.resourceCreateTime)}
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">链路追踪</label>
                  <div className="mt-1 text-sm text-slate-600">{detail.traceId || "-"}</div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">会话 / 消息</label>
                  <div className="mt-1 text-sm text-slate-600">
                    {detail.conversationId || "-"} / {detail.messageId || "-"}
                  </div>
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">内容描述</label>
                <div className="mt-1 max-h-40 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
                  {detail.description || detail.snippet || "-"}
                </div>
              </div>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}