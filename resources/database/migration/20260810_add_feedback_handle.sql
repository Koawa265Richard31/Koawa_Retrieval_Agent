-- 2026-08-10: 用户反馈治理 —— 反馈表增加处理状态/处理备注/处理人，支撑管理控制台反馈处理闭环
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS handled SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS handle_note VARCHAR(512);
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS handle_time TIMESTAMP;
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS handler_id VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_feedback_handled_time ON t_message_feedback (handled, create_time DESC);
COMMENT ON COLUMN t_message_feedback.handled IS '是否已处理 0：未处理 1：已处理';
COMMENT ON COLUMN t_message_feedback.handle_note IS '处理备注';
COMMENT ON COLUMN t_message_feedback.handle_time IS '处理时间';
COMMENT ON COLUMN t_message_feedback.handler_id IS '处理人ID';
