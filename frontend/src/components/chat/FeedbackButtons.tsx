import { useState } from "react";
import { Copy, Star, ThumbsDown, ThumbsUp } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { FeedbackValue } from "@/types";

interface FeedbackButtonsProps {
  messageId: string;
  feedback: FeedbackValue;
  rating?: number | null;
  content: string;
  className?: string;
  alwaysVisible?: boolean;
}

const DISLIKE_REASONS = [
  "回答错误",
  "答非所问",
  "信息过时",
  "不够详细",
  "图片/格式问题",
  "其他"
];

export function FeedbackButtons({
  messageId,
  feedback,
  rating,
  content,
  className,
  alwaysVisible
}: FeedbackButtonsProps) {
  const submitFeedback = useChatStore((state) => state.submitFeedback);
  const [dislikeOpen, setDislikeOpen] = useState(false);
  const [dislikeReason, setDislikeReason] = useState("");
  const [dislikeComment, setDislikeComment] = useState("");
  const [pendingRating, setPendingRating] = useState<number | null>(null);
  const [hoverRating, setHoverRating] = useState(0);

  const resetDislikeForm = () => {
    setDislikeReason("");
    setDislikeComment("");
  };

  const handleFeedback = (value: FeedbackValue) => {
    const next = feedback === value ? null : value;
    if (next === "dislike") {
      resetDislikeForm();
      setDislikeOpen(true);
      return;
    }
    submitFeedback(messageId, next, null, null, rating).catch(() => null);
  };

  const handleStarClick = (value: number) => {
    if (value <= 3) {
      if (rating === value) {
        submitFeedback(messageId, null, null, null, null).catch(() => null);
        return;
      }
      setPendingRating(value);
      resetDislikeForm();
      setDislikeOpen(true);
      return;
    }
    const next = rating === value ? null : value;
    if (next === null) {
      submitFeedback(messageId, null, null, null, null).catch(() => null);
    } else {
      submitFeedback(messageId, "like", null, null, value).catch(() => null);
    }
  };

  const handleDislikeSubmit = async () => {
    const reason = dislikeReason.trim() || null;
    const comment = dislikeComment.trim() || null;
    if (!reason && !comment) {
      toast.error("请选择问题类型或填写说明");
      return;
    }
    setDislikeOpen(false);
    await submitFeedback(messageId, "dislike", reason, comment, pendingRating).catch(() => null);
    setPendingRating(null);
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      toast.success("复制成功");
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <>
      <div
        className={cn(
          "flex items-center gap-0.5",
          alwaysVisible ? "opacity-100" : "opacity-0 group-hover:opacity-100",
          className
        )}
        role="radiogroup"
        aria-label="回答满意度"
      >
        {[1, 2, 3, 4, 5].map((star) => {
          const filled = (hoverRating || rating || 0) >= star;
          return (
            <button
              key={star}
              type="button"
              onClick={() => handleStarClick(star)}
              onMouseEnter={() => setHoverRating(star)}
              onMouseLeave={() => setHoverRating(0)}
              aria-label={`${star} 星${filled ? "（已选）" : ""}`}
              className="flex h-6 w-6 items-center justify-center rounded hover:bg-amber-50"
            >
              <Star
                className={cn(
                  "h-4 w-4 transition-colors",
                  filled ? "fill-amber-400 text-amber-400" : "text-[#cccccc] hover:text-amber-300"
                )}
              />
            </button>
          );
        })}
        <span className="ml-1 text-[11px] text-slate-400">
          {rating ? `${rating} 星` : "满意度"}
        </span>
      </div>

      <div
        className={cn(
          "flex items-center gap-1 transition-opacity",
          alwaysVisible ? "opacity-100" : "opacity-0 group-hover:opacity-100",
          className
        )}
      >
        <Button
          variant="ghost"
          size="icon"
          onClick={handleCopy}
          aria-label="复制内容"
          className="h-8 w-8 text-[#999999] hover:bg-[#F5F5F5] hover:text-[#666666]"
        >
          <Copy className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={() => handleFeedback("like")}
          aria-label="点赞"
          className={cn(
            "h-8 w-8 text-[#999999] hover:text-[#10B981] hover:bg-[#F5F5F5]",
            feedback === "like" && "text-[#10B981]"
          )}
        >
          <ThumbsUp className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={() => handleFeedback("dislike")}
          aria-label="点踩并反馈问题"
          className={cn(
            "h-8 w-8 text-[#999999] hover:text-[#EF4444] hover:bg-[#F5F5F5]",
            feedback === "dislike" && "text-[#EF4444]"
          )}
        >
          <ThumbsDown className="h-4 w-4" />
        </Button>
      </div>

      <Dialog
        open={dislikeOpen}
        onOpenChange={(open) => {
          setDislikeOpen(open);
          if (!open) {
            resetDislikeForm();
            setPendingRating(null);
          }
        }}
      >
        <DialogContent className="sm:max-w-[440px]">
          <DialogHeader>
            <DialogTitle>{pendingRating ? "满意度反馈" : "反馈问题"}</DialogTitle>
            <DialogDescription>
              {pendingRating
                ? `你为这条回答打了 ${pendingRating} 星，请告诉我们哪里不满意`
                : "感谢你的反馈，我们会尽快改进回答质量"}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">问题类型</label>
              <div className="flex flex-wrap gap-2">
                {DISLIKE_REASONS.map((reason) => {
                  const active = dislikeReason === reason;
                  return (
                    <button
                      key={reason}
                      type="button"
                      onClick={() => setDislikeReason(active ? "" : reason)}
                      className={cn(
                        "rounded-full border px-3 py-1.5 text-sm transition-colors",
                        active
                          ? "border-rose-300 bg-rose-50 text-rose-600"
                          : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
                      )}
                    >
                      {reason}
                    </button>
                  );
                })}
              </div>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-700">
                补充说明
                <span className="ml-1 text-xs text-slate-400">（可选）</span>
              </label>
              <Textarea
                value={dislikeComment}
                onChange={(event) => setDislikeComment(event.target.value)}
                placeholder="例如：回答里提到的卡面信息不准确……"
                className="min-h-[96px]"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDislikeOpen(false)}>
              取消
            </Button>
            <Button onClick={handleDislikeSubmit}>提交反馈</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
