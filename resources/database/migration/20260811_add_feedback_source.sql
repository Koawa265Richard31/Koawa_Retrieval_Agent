-- 2026-08-11: 反馈来源区分 —— 主聊天(chat) 与 粉丝页(fan) 分别管理
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'chat';
CREATE INDEX IF NOT EXISTS idx_feedback_source_time ON t_message_feedback (source, create_time DESC);
COMMENT ON COLUMN t_message_feedback.source IS '反馈来源 chat=主聊天 fan=粉丝页';
