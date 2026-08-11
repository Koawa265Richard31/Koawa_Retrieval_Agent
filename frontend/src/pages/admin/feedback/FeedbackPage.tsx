import { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  Check,
  Clock,
  Download,
  Eye,
  MessageSquare,
  RefreshCw,
  RefreshCcw,
  Star,
  ThumbsDown,
  ThumbsUp,
  TrendingUp,
  Workflow,
  Undo2
} from "lucide-react";
import { toast } from "sonner";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
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
import { Textarea } from "@/components/ui/textarea";
import { RelativeTime } from "@/components/RelativeTime";
import {
  getFeedbackCategoryStats,
  getFeedbackGovernance,
  getFeedbackPage,
  getFeedbackStats,
  handleFeedback,
  unhandleFeedback,
  type CategoryStat,
  type GovernanceItem,
  type FeedbackStats,
  type MessageFeedback,
  type PageResult
} from "@/services/feedbackService";
import { cn } from "@/lib/utils";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

function VoteBadge({ vote }: { vote: number }) {
  if (vote === 1) {
    return (
      <Badge
        variant="outline"
        className="border-emerald-200 bg-emerald-50 text-emerald-600"
      >
        <ThumbsUp className="mr-1 h-3 w-3" />
        点赞
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className="border-rose-200 bg-rose-50 text-rose-600">
      <ThumbsDown className="mr-1 h-3 w-3" />
      点踩
    </Badge>
  );
}

function RatingBadge({ rating }: { rating?: number | null }) {
  if (rating == null) return null;
  return (
    <Badge
      variant="outline"
      className={cn(
        "ml-1 border-amber-200 bg-amber-50 text-amber-600",
        rating <= 3 && "border-rose-200 bg-rose-50 text-rose-600"
      )}
    >
      <Star className="mr-1 h-3 w-3 fill-amber-400 text-amber-400" />
      {rating} 星
    </Badge>
  );
}

function HandledBadge({ handled }: { handled: number }) {
  if (handled === 1) {
    return <Badge variant="outline" className="admin-status-success">已处理</Badge>;
  }
  return <Badge variant="outline" className="admin-status-muted">未处理</Badge>;
}

export function FeedbackPage() {
  const [pageData, setPageData] = useState<PageResult<MessageFeedback> | null>(null);
  const [stats, setStats] = useState<FeedbackStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [keyword, setKeyword] = useState("");
  const [voteFilter, setVoteFilter] = useState<number | null>(null);
  const [ratingFilter, setRatingFilter] = useState<number | null>(null);
  const isFanScope = window.location.pathname.startsWith("/fan/admin");
  const [sourceFilter, setSourceFilter] = useState<string>(isFanScope ? "fan" : "all");
  const [handledFilter, setHandledFilter] = useState<number | null>(null);
  const [detail, setDetail] = useState<MessageFeedback | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [handleNote, setHandleNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [categoryStats, setCategoryStats] = useState<CategoryStat[]>([]);
  const [reasonFilter, setReasonFilter] = useState<string | null>(null);
  const [governance, setGovernance] = useState<GovernanceItem[]>([]);
  const [governanceHandled, setGovernanceHandled] = useState<number | null>(0);
  const [governanceLoading, setGovernanceLoading] = useState(false);
  const navigate = useNavigate();

  const loadData = useCallback(async (current = pageNo, kw = keyword, vote = voteFilter, handled = handledFilter, rating = ratingFilter, source = sourceFilter, reason = reasonFilter) => {
    try {
      setLoading(true);
      const data = await getFeedbackPage({
        current,
        size: PAGE_SIZE,
        vote,
        handled,
        rating,
        source: source === "all" ? null : source,
        reason: reason || undefined,
        keyword: kw || undefined
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载反馈失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, voteFilter, ratingFilter, sourceFilter, handledFilter, reasonFilter]);

  const loadStats = useCallback(async () => {
    try {
      const data = await getFeedbackStats();
      setStats(data);
    } catch (error) {
      console.error(error);
    }
  }, []);
  const loadGovernance = useCallback(async (handled = governanceHandled) => {
    try {
      setGovernanceLoading(true);
      const data = await getFeedbackGovernance(handled);
      setGovernance(data);
    } catch (error) {
      console.error(error);
    } finally {
      setGovernanceLoading(false);
    }
  }, [governanceHandled]);

  const loadCategoryStats = useCallback(async () => {
    try {
      const data = await getFeedbackCategoryStats();
      setCategoryStats(data);
    } catch (error) {
      console.error(error);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    loadCategoryStats();
  }, [loadCategoryStats]);

  useEffect(() => {
    loadGovernance();
  }, [loadGovernance]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchKeyword.trim());
  };

  const handleRefresh = () => {
    loadData(1, keyword, voteFilter, handledFilter, ratingFilter, sourceFilter, reasonFilter);
    loadStats();
    loadCategoryStats();
    loadGovernance();
  };

  const toggleReasonFilter = (reason: string) => {
    setReasonFilter((prev) => (prev === reason ? null : reason));
    setPageNo(1);
  };

  const exportGovernanceCsv = () => {
    if (governance.length === 0) return;
    const header = [
      "文档ID", "文档名", "知识库", "内容ID", "源类型", "源地址", "点踩数", "未处理数", "最近反馈", "涉及问题"
    ];
    const rows = governance.map((item) => [
      item.docId,
      item.docName,
      item.kbId || "",
      item.contentId || "",
      item.sourceType || "",
      item.sourceLocation || "",
      String(item.dislikeCount),
      String(item.unhandledCount),
      item.recentTime || "",
      (item.sampleQuestions || []).join(" | ")
    ]);
    const csv = [header, ...rows]
      .map((row) => row.map((cell) => `"${String(cell ?? "").replace(/"/g, "\"\"")}"`).join(","))
      .join("\n");
    const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `待治理文档-${new Date().toISOString().slice(0, 10)}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const copyReCrawlIds = async () => {
    const ids = governance
      .filter((item) => item.reCrawlable && item.contentId)
      .map((item) => item.contentId as string);
    if (ids.length === 0) {
      toast.error("当前无 GameKee 源站内容可定点重采");
      return;
    }
    try {
      await navigator.clipboard.writeText(ids.join(","));
      toast.success(`已复制 ${ids.length} 个内容ID：crawl_gamekee_gakumas.py --ids ${ids.join(",")}`);
    } catch {
      toast.error("复制失败");
    }
  };

  const applyVoteFilter = (value: string) => {
    const next = value === "all" ? null : Number(value);
    setVoteFilter(next);
    setPageNo(1);
  };

  const applyRatingFilter = (value: string) => {
    const next = value === "all" ? null : Number(value);
    setRatingFilter(next);
    setPageNo(1);
  };

  const applySourceFilter = (value: string) => {
    setSourceFilter(value);
    setPageNo(1);
  };

  const applyHandledFilter = (value: string) => {
    const next = value === "all" ? null : Number(value);
    setHandledFilter(next);
    setPageNo(1);
  };

  const openDetail = (item: MessageFeedback) => {
    setDetail(item);
    setHandleNote(item.handleNote || "");
    setDetailOpen(true);
  };

  const submitHandle = async () => {
    if (!detail) return;
    try {
      setSubmitting(true);
      await handleFeedback(detail.id, handleNote.trim() || undefined);
      toast.success("已标记为处理");
      setDetailOpen(false);
      await loadData(pageNo, keyword, voteFilter, handledFilter, ratingFilter, sourceFilter, reasonFilter);
      await loadStats();
      await loadCategoryStats();
      await loadGovernance();
    } catch (error) {
      toast.error(getErrorMessage(error, "处理失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  const submitUnhandle = async (item: MessageFeedback) => {
    try {
      await unhandleFeedback(item.id);
      toast.success("已取消处理");
      setDetailOpen(false);
      await loadData(pageNo, keyword, voteFilter, handledFilter, ratingFilter, sourceFilter, reasonFilter);
      await loadStats();
      await loadCategoryStats();
      await loadGovernance();
    } catch (error) {
      toast.error(getErrorMessage(error, "取消失败"));
      console.error(error);
    }
  };

  const records = pageData?.records || [];

  const statCards = [
    { label: "全部反馈", value: stats?.total ?? 0, icon: MessageSquare, iconClass: "bg-indigo-50 text-indigo-600" },
    { label: "点赞", value: stats?.likeCount ?? 0, icon: ThumbsUp, iconClass: "bg-emerald-50 text-emerald-600" },
    { label: "点踩", value: stats?.dislikeCount ?? 0, icon: ThumbsDown, iconClass: "bg-rose-50 text-rose-600" },
    { label: "平均满意度", value: stats?.avgRating != null ? `${Number(stats.avgRating).toFixed(2)} ★` : "—", icon: Star, iconClass: "bg-amber-50 text-amber-600" },
    { label: "低分反馈(<4星)", value: stats?.lowRatingCount ?? 0, icon: TrendingUp, iconClass: "bg-orange-50 text-orange-600" },
    { label: "未处理", value: stats?.unhandledCount ?? 0, icon: Clock, iconClass: "bg-amber-50 text-amber-600" },
    { label: "已处理", value: stats?.handledCount ?? 0, icon: CheckCircle2, iconClass: "bg-teal-50 text-teal-600" },
    { label: "今日新增", value: stats?.todayCount ?? 0, icon: TrendingUp, iconClass: "bg-orange-50 text-orange-600" }
  ];

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">用户反馈</h1>
          <p className="admin-page-subtitle">查看并处理用户对回答的点赞/点踩/星级反馈</p>
        </div>
        <div className="admin-page-actions">
          <Input
            value={searchKeyword}
            onChange={(event) => setSearchKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") handleSearch();
            }}
            placeholder="搜索用户/问题/回答/反馈内容"
            className="w-[260px]"
          />
          <Button variant="outline" onClick={handleSearch}>
            搜索
          </Button>
          <Button variant="outline" onClick={handleRefresh}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      <div className="admin-stat-grid">
        {statCards.map((card) => {
          const Icon = card.icon;
          return (
            <div key={card.label} className="admin-stat-card">
              <div>
                <div className="admin-stat-label">{card.label}</div>
                <div className="admin-stat-value">{card.value}</div>
              </div>
              <div className={cn("admin-stat-icon", card.iconClass)}>
                <Icon className="h-5 w-5" />
              </div>
            </div>
          );
        })}
      </div>


      <Card>
        <CardContent className="pt-6">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-semibold text-slate-800">反馈分类治理</h2>
              <p className="text-xs text-slate-500">按问题类型聚合点踩反馈，点击分类可筛选列表</p>
            </div>
            {reasonFilter ? (
              <Button variant="ghost" size="sm" onClick={() => toggleReasonFilter(reasonFilter)}>
                清除「{reasonFilter}」筛选
              </Button>
            ) : null}
          </div>
          {categoryStats.length === 0 ? (
            <div className="py-6 text-center text-sm text-muted-foreground">暂无分类数据，用户点踩后自动聚合</div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {categoryStats.map((item) => {
                const active = reasonFilter === item.reason;
                const max = Math.max(...categoryStats.map((c) => c.dislikeCount), 1);
                const ratio = Math.round((item.dislikeCount / max) * 100);
                return (
                  <button
                    key={item.reason}
                    type="button"
                    onClick={() => toggleReasonFilter(item.reason)}
                    className={cn(
                      "rounded-xl border p-3 text-left transition-colors",
                      active
                        ? "border-rose-300 bg-rose-50"
                        : "border-slate-200 bg-white hover:border-slate-300"
                    )}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium text-slate-800">{item.reason}</span>
                      <span className="text-xs text-rose-600">{item.dislikeCount} 踩</span>
                    </div>
                    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-rose-400" style={{ width: `${ratio}%` }} />
                    </div>
                    <div className="mt-2 flex items-center justify-between text-xs text-slate-500">
                      <span>
                        未处理 <span className="font-medium text-amber-600">{item.unhandledCount}</span>
                      </span>
                      <span>总 {item.totalCount}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
      <Card>
      <Card>
        <CardContent className="pt-6">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-sm font-semibold text-slate-800">待治理文档</h2>
              <p className="text-xs text-slate-500">按链路检索命中的文档归集点踩反馈，定位需核对/重采的语料</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={exportGovernanceCsv}
                disabled={governance.length === 0}
              >
                <Download className="mr-1 h-4 w-4" />
                导出 CSV
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={copyReCrawlIds}
                disabled={governance.length === 0}
              >
                <RefreshCcw className="mr-1 h-4 w-4" />
                重采清单
              </Button>
              <Select
              value={governanceHandled === null ? "all" : String(governanceHandled)}
              onValueChange={(value) => setGovernanceHandled(value === "all" ? null : Number(value))}
            >
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="全部状态" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="0">仅未处理</SelectItem>
                <SelectItem value="1">仅已处理</SelectItem>
                <SelectItem value="all">全部</SelectItem>
              </SelectContent>
            </Select>
          </div>
          </div>
          {governanceLoading ? (
            <div className="py-6 text-center text-sm text-muted-foreground">加载中...</div>
          ) : governance.length === 0 ? (
            <div className="py-6 text-center text-sm text-muted-foreground">
              暂无待治理文档，点踩反馈落库并产生新检索链路后自动归集
            </div>
          ) : (
            <Table className="min-w-[900px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[260px]">文档</TableHead>
                  <TableHead className="w-[140px]">知识库</TableHead>
                  <TableHead className="w-[90px]">点踩</TableHead>
                  <TableHead className="w-[100px]">未处理</TableHead>
                  <TableHead className="w-[150px]">最近反馈</TableHead>
                  <TableHead>涉及问题</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {governance.map((item) => (
                  <TableRow key={item.docId}>
                    <TableCell>
                      <div className="max-w-[240px] truncate font-medium text-slate-800" title={item.docName}>
                        {item.docName}
                      </div>
                      <div className="text-[11px] text-slate-400">{item.docId}</div>
                    </TableCell>
                    <TableCell className="text-xs text-slate-500">{item.kbId || "-"}</TableCell>
                    <TableCell>
                      <span className="font-semibold text-rose-600">{item.dislikeCount}</span>
                    </TableCell>
                    <TableCell>
                      <span className="font-medium text-amber-600">{item.unhandledCount}</span>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <RelativeTime value={item.recentTime} />
                    </TableCell>
                    <TableCell className="max-w-[320px]">
                      <span
                        className="line-clamp-2 text-xs text-slate-600"
                        title={(item.sampleQuestions || []).join(" | ")}
                      >
                        {(item.sampleQuestions || []).join("；") || "-"}
                      </span>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
        <CardContent className="pt-6">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            {[
              { value: "all", label: "全部来源" },
              { value: "chat", label: "主聊天" },
              { value: "fan", label: "粉丝页" }
            ].map((tab) => (
              <button
                key={tab.value}
                type="button"
                onClick={() => applySourceFilter(tab.value)}
                className={cn(
                  "rounded-full border px-3 py-1.5 text-sm transition-colors",
                  sourceFilter === tab.value
                    ? "border-indigo-300 bg-indigo-50 text-indigo-700"
                    : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <Select value={voteFilter === null ? "all" : String(voteFilter)} onValueChange={applyVoteFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="全部反馈类型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部反馈类型</SelectItem>
                <SelectItem value="1">点赞</SelectItem>
                <SelectItem value="-1">点踩</SelectItem>
              </SelectContent>
            </Select>
            <Select value={handledFilter === null ? "all" : String(handledFilter)} onValueChange={applyHandledFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="全部状态" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部状态</SelectItem>
                <SelectItem value="0">未处理</SelectItem>
                <SelectItem value="1">已处理</SelectItem>
              </SelectContent>
            </Select>
            <Select value={ratingFilter === null ? "all" : String(ratingFilter)} onValueChange={applyRatingFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="全部星级" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部星级</SelectItem>
                <SelectItem value="1">1 星</SelectItem>
                <SelectItem value="2">2 星</SelectItem>
                <SelectItem value="3">3 星</SelectItem>
                <SelectItem value="4">4 星</SelectItem>
                <SelectItem value="5">5 星</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : records.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">暂无反馈记录</div>
          ) : (
            <Table className="min-w-[1080px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[110px]">用户</TableHead>
                  <TableHead className="w-[110px]">反馈</TableHead>
                  <TableHead className="w-[160px]">反馈内容</TableHead>
                  <TableHead className="w-[200px]">问题</TableHead>
                  <TableHead className="w-[220px]">回答</TableHead>
                  <TableHead className="w-[120px]">状态</TableHead>
                  <TableHead className="w-[150px]">时间</TableHead>
                  <TableHead className="w-[150px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {records.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.username || item.userId || "-"}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap items-center gap-1">
                        <VoteBadge vote={item.vote} />
                        <RatingBadge rating={item.rating} />
                      </div>
                    </TableCell>
                    <TableCell className="max-w-[150px]">
                      <span className="line-clamp-2 text-xs text-slate-600" title={[item.reason, item.comment].filter(Boolean).join(" | ") || ""}>
                        {[item.reason, item.comment].filter(Boolean).join("；") || "-"}
                      </span>
                    </TableCell>
                    <TableCell className="max-w-[190px]">
                      <span className="line-clamp-2 text-xs" title={item.question || ""}>
                        {item.question || "-"}
                      </span>
                    </TableCell>
                    <TableCell className="max-w-[210px]">
                      <span className="line-clamp-2 text-xs text-slate-500" title={item.answer || ""}>
                        {item.answer || "-"}
                      </span>
                    </TableCell>
                    <TableCell>
                      <HandledBadge handled={item.handled} />
                      {item.handled === 1 && item.handlerName ? (
                        <div className="mt-1 text-[11px] text-slate-400">{item.handlerName}</div>
                      ) : null}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <RelativeTime value={item.createTime} />
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button variant="outline" size="sm" onClick={() => openDetail(item)}>
                          <Eye className="mr-1 h-3.5 w-3.5" />
                          详情
                        </Button>
                        {item.handled === 0 ? (
                          <Button size="sm" onClick={() => openDetail(item)}>
                            <Check className="mr-1 h-3.5 w-3.5" />
                            处理
                          </Button>
                        ) : (
                          <Button variant="ghost" size="sm" onClick={() => submitUnhandle(item)}>
                            <Undo2 className="mr-1 h-3.5 w-3.5" />
                            取消处理
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {pageData ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}
              disabled={pageData.current <= 1}
            >
              上一页
            </Button>
            <span>
              {pageData.current} / {pageData.pages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))}
              disabled={pageData.current >= pageData.pages}
            >
              下一页
            </Button>
          </div>
        </div>
      ) : null}

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="sm:max-w-[680px]">
          <DialogHeader>
            <DialogTitle>反馈详情</DialogTitle>
            <DialogDescription>
              {detail ? (
                <span className="flex items-center gap-2">
                  <VoteBadge vote={detail.vote} />
                  <RatingBadge rating={detail.rating} />
                  <HandledBadge handled={detail.handled} />
                  <span className="text-slate-400">用户：{detail.username || detail.userId || "-"}</span>
                </span>
              ) : null}
            </DialogDescription>
          </DialogHeader>
          {detail ? (
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-slate-700">问题</label>
                <div className="mt-1 max-h-32 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
                  {detail.question || "-"}
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">回答</label>
                <div className="mt-1 max-h-48 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm leading-relaxed text-slate-700">
                  {detail.answer || "-"}
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="text-sm font-medium text-slate-700">问题类型</label>
                  <div className="mt-1 text-sm text-slate-600">{detail.reason || "-"}</div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">补充说明</label>
                  <div className="mt-1 text-sm text-slate-600">{detail.comment || "-"}</div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">反馈时间</label>
                  <div className="mt-1 text-sm text-slate-600">
                    <RelativeTime value={detail.createTime} />
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">处理信息</label>
                  <div className="mt-1 text-sm text-slate-600">
                    {detail.handled === 1 ? (
                      <>
                        {detail.handlerName || detail.handlerId || "-"}
                        {detail.handleTime ? (
                          <>
                            {" · "}
                            <RelativeTime value={detail.handleTime} />
                          </>
                        ) : null}
                      </>
                    ) : (
                      "未处理"
                    )}
                  </div>
                </div>
              </div>
              {detail.handled === 1 && detail.handleNote ? (
                <div>
                  <label className="text-sm font-medium text-slate-700">处理备注</label>
                  <div className="mt-1 whitespace-pre-wrap rounded-lg bg-teal-50 p-3 text-sm text-teal-800">
                    {detail.handleNote}
                  </div>
                </div>
              ) : null}
              {detail.handled === 0 ? (
                <div>
                  <label className="text-sm font-medium text-slate-700">
                    处理备注
                    <span className="ml-1 text-xs text-slate-400">（可选）</span>
                  </label>
                  <Textarea
                    value={handleNote}
                    onChange={(event) => setHandleNote(event.target.value)}
                    placeholder="例如：已核对知识库，修正该卡面信息后回复用户……"
                    className="mt-1 min-h-[80px]"
                  />
                </div>
              ) : null}
            </div>
          ) : null}
          <DialogFooter>
            {detail?.traceId ? (
              <Button variant="outline" onClick={() => navigate(`/admin/traces/${detail.traceId}`)}>
                <Workflow className="mr-1 h-4 w-4" />
                查看链路追踪
              </Button>
            ) : null}
            {detail?.handled === 1 ? (
              <Button variant="outline" onClick={() => submitUnhandle(detail)}>
                <Undo2 className="mr-1 h-4 w-4" />
                取消处理
              </Button>
            ) : (
              <Button onClick={submitHandle} disabled={submitting}>
                <Check className="mr-1 h-4 w-4" />
                {submitting ? "处理中..." : "标记已处理"}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}


