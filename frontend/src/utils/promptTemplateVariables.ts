import type { PromptTemplateVariableItem } from '@/utils/promptTemplateTypes'

export function parseTemplateVariables(raw: unknown): PromptTemplateVariableItem[] {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return []
  }
  return Object.entries(raw as Record<string, unknown>).map(([key, value]) => {
    if (typeof value === 'string') {
      return { key, type: value, required: true }
    }
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { key, type: 'string', required: true }
    }
    const node = value as Record<string, unknown>
    return {
      key,
      type: typeof node.type === 'string' ? node.type : 'string',
      required: node.required === undefined ? true : Boolean(node.required),
      description: typeof node.description === 'string' ? node.description : undefined,
    }
  })
}

export function validateTemplateVariableValues(
  variables: PromptTemplateVariableItem[],
  values: Record<string, string>,
) {
  const missing = variables
    .filter((item) => item.required)
    .map((item) => item.key)
    .filter((key) => !values[key]?.trim())
  if (missing.length > 0) {
    return { ok: false as const, message: `请填写模板变量：${missing.join('、')}` }
  }
  return { ok: true as const }
}

export function variableFieldLabel(key: string) {
  return key.replace(/_/g, ' ')
}
