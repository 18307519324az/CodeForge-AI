-- Dev AI 验收专用：不预置用户，首个注册用户由 AuthApplicationService 授予 PLATFORM_ADMIN。
ALTER TABLE generation_task
    ADD COLUMN IF NOT EXISTS requirement TEXT;
