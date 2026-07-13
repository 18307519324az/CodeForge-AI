ALTER TABLE generation_task_event
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '请求ID' AFTER event_payload_json;

CREATE INDEX idx_generation_task_event_request_id ON generation_task_event (request_id);
