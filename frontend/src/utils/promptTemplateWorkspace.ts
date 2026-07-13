import type { SelectedPromptTemplate } from '@/utils/promptTemplateTypes'

export interface PromptTemplateRouteQuery {
  templateId?: string | string[]
  templateVersionId?: string | string[]
}

export function resolveRouteTemplateId(query: PromptTemplateRouteQuery) {
  const raw = query.templateId
  if (Array.isArray(raw)) return raw[0] || ''
  return raw || ''
}

export function resolveRouteTemplateVersionId(query: PromptTemplateRouteQuery) {
  const raw = query.templateVersionId
  if (Array.isArray(raw)) return raw[0] || ''
  return raw || ''
}

export function buildHomeRouteWithTemplate(templateId: number, templateVersionId: number) {
  return {
    name: 'home',
    query: {
      templateId: String(templateId),
      templateVersionId: String(templateVersionId),
    },
  }
}

export function buildGenerateRouteWithTemplate(appId: string, templateId: number, templateVersionId: number) {
  return `/apps/${appId}/generate?templateId=${templateId}&templateVersionId=${templateVersionId}`
}

export function buildGenerationTaskTemplatePayload(selection: SelectedPromptTemplate | null) {
  if (!selection) {
    return {}
  }
  const payload: {
    promptTemplateId: number
    promptTemplateVersionId: number
    promptTemplateVersionNo: number
    templateVariables?: Record<string, string>
  } = {
    promptTemplateId: selection.templateId,
    promptTemplateVersionId: selection.templateVersionId,
    promptTemplateVersionNo: selection.versionNo,
  }
  if (Object.keys(selection.variableValues).length > 0) {
    payload.templateVariables = { ...selection.variableValues }
  }
  return payload
}

export function requirementRemainsIndependent(requirement: string, templateExample?: string) {
  const normalizedRequirement = requirement.trim()
  const normalizedExample = (templateExample || '').trim()
  if (!normalizedRequirement) return true
  if (!normalizedExample) return true
  return normalizedRequirement !== normalizedExample
}
