-- 2026-08-11: 用户反馈满意度星级 —— 反馈表增加 rating(1-5)，支撑聊天星级反馈与管理台满意度统计
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS rating SMALLINT;
CREATE INDEX IF NOT EXISTS idx_feedback_rating_time ON t_message_feedback (rating, create_time DESC);
COMMENT ON COLUMN t_message_feedback.rating IS '满意度星级 1-5（可选，仅星级反馈填写）';
