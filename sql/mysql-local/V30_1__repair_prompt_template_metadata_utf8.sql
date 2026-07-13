-- Repair corrupted prompt_template metadata written with wrong client charset.
-- Corruption fingerprint (template id=1):
--   template_name HEX: 56756520E6A4A4E59CADE6B4B0E990A2E786B8E59E9A
-- Correct UTF-8 HEX:
--   template_name: 56756520E9A1B9E79BAEE7949FE68890
--   remark:        E9809AE794A82056756520E9A1B9E79BAEE6A8A1E69DBF
-- Does NOT touch prompt_template_version bodies repaired by V29.

UPDATE prompt_template
SET template_name = 'Vue 项目生成',
    remark = '通用 Vue 项目模板',
    description = '生成 Vue 前端项目代码',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1
  AND is_deleted = 0
  AND HEX(template_name) = '56756520E6A4A4E59CADE6B4B0E990A2E786B8E59E9A';

UPDATE prompt_template
SET template_name = 'API 接口生成',
    remark = 'Spring Boot API 模板',
    description = '生成 Spring Boot API 项目',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 2
  AND is_deleted = 0
  AND HEX(template_name) = '41504920E98EBAE383A5E5BD9BE990A2E786B8E59E9A';
