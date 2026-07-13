MERGE INTO model_provider (
    id, provider_code, provider_name, base_url, auth_mode, api_protocol, api_key_env,
    default_model, priority, status, created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    1, 'openai', 'OpenAI Compatible', 'https://api.openai.com/v1', 'API_KEY', 'OPENAI_COMPATIBLE',
    'OPENAI_API_KEY', 'gpt-4.1-mini', 10, 'ACTIVE', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

MERGE INTO model_provider (
    id, provider_code, provider_name, base_url, auth_mode, api_protocol, api_key_env,
    default_model, priority, status, created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    2, 'rule', 'Rule Generator', NULL, 'NONE', 'RULE_BASED', NULL,
    'rule-based', 999, 'ACTIVE', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);
