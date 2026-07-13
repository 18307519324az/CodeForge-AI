-- Align H2 test schema with MySQL B33 prompt_template_version.status
ALTER TABLE prompt_template_version
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

UPDATE prompt_template_version
SET status = 'PUBLISHED'
WHERE published_at IS NOT NULL
  AND status = 'DRAFT';
