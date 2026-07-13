-- Repair locally corrupted prompt_template_version text for template 1.
-- Only updates rows that still contain replacement markers from prior charset damage.
UPDATE prompt_template_version
SET system_prompt = '你是专业的 Vue 代码生成助手。请根据用户需求生成结构清晰、可直接运行的 Vue 项目。',
    user_prompt = '请生成一个 {{app_name}} Vue 项目',
    variables_json = JSON_OBJECT(
            'app_name', JSON_OBJECT(
                    'type', 'string',
                    'required', TRUE,
                    'description', '应用名称'
            )
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE template_id = 1
  AND version_no = 1
  AND is_deleted = 0
  AND (user_prompt LIKE '%?%' OR system_prompt LIKE '%?%' OR system_prompt LIKE '%浣%');
