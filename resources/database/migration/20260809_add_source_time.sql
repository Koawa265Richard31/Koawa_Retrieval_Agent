-- 2026-08-09: KB 时效治理 —— 文档/分块增加源端时间，检索按时间加权
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS source_time TIMESTAMP;
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS source_time TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_doc_source_time ON t_knowledge_document (source_time);
CREATE INDEX IF NOT EXISTS idx_chunk_source_time ON t_knowledge_chunk (source_time);
COMMENT ON COLUMN t_knowledge_document.source_time IS '源端发布时间/更新时间（攻略/档案时效基准）';
COMMENT ON COLUMN t_knowledge_chunk.source_time IS '源端发布时间/更新时间（继承文档）';
