-- 2026-08-11: Agent 联网搜索记录表 —— 记录 LLM 联网访问过的网址，与原始问题绑定，含访问时间/资源创建时间/描述
CREATE TABLE IF NOT EXISTS t_web_search_record (
    id                   VARCHAR(32) PRIMARY KEY,
    trace_id             VARCHAR(64),
    conversation_id      VARCHAR(64),
    message_id           VARCHAR(64),
    question             TEXT NOT NULL,
    provider             VARCHAR(16),
    query                TEXT,
    url                  TEXT NOT NULL,
    url_title            VARCHAR(512),
    description          TEXT,
    snippet              TEXT,
    visit_time           TIMESTAMP,
    resource_create_time TIMESTAMP,
    create_time          TIMESTAMP DEFAULT now(),
    deleted              SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_web_search_question ON t_web_search_record (question);
CREATE INDEX IF NOT EXISTS idx_web_search_trace ON t_web_search_record (trace_id);
CREATE INDEX IF NOT EXISTS idx_web_search_visit ON t_web_search_record (visit_time DESC);
COMMENT ON TABLE t_web_search_record IS 'Agent 联网搜索访问记录（网址与原始问题绑定，含访问时间/资源创建时间/描述）';
COMMENT ON COLUMN t_web_search_record.question IS '原始问题（绑定）';
COMMENT ON COLUMN t_web_search_record.url IS 'LLM 联网访问过的网址';
COMMENT ON COLUMN t_web_search_record.description IS '网址对应内容描述';
COMMENT ON COLUMN t_web_search_record.visit_time IS '访问时间';
COMMENT ON COLUMN t_web_search_record.resource_create_time IS '网址对应资源的创建时间';
