import request from '@/request'
import type { WorkspaceSummaryResponse } from '@/api/workspace'
import { listWorkspaces } from '@/api/workspace'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

interface PageResponse<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface PromptTemplateAdminListItem {
  id: number
  workspaceId: number
  templateName: string
  templateScene: string
  status: string
  currentVersionNo: number
  versionCount?: number
  updatedAt?: string
}

export interface PromptTemplateAdminVersion {
  id: number
  templateId: number
  versionNo: number
  versionStatus?: string
  systemPrompt: string
  userPrompt: string
  variablesJson?: string
  modelStrategyJson?: string
  publishedBy?: number
  publishedAt?: string
}

export interface PromptTemplateAdminDetail {
  id: number
  workspaceId: number
  templateName: string
  templateScene: string
  status: string
  currentVersionNo: number
  remark?: string
  currentVersion?: PromptTemplateAdminVersion
}

export interface PromptTemplateAdminQuery {
  workspaceId?: number
  keyword?: string
  templateScene?: string
  status?: string
  pageNo?: number
  pageSize?: number
}

export interface PromptTemplateCreatePayload {
  workspaceId: number
  templateName: string
  templateScene: string
  systemPrompt: string
  userPrompt: string
  remark?: string
}

export interface PromptTemplateUpdatePayload {
  templateName: string
  templateScene: string
  remark?: string
}

export interface PromptTemplateVersionPayload {
  systemPrompt: string
  userPrompt: string
  variablesJson?: string
  modelStrategyJson?: string
}

export const listAdminPromptTemplates = (params?: PromptTemplateAdminQuery) =>
  request<ApiResponse<PageResponse<PromptTemplateAdminListItem>>>({
    url: '/prompt-templates',
    method: 'GET',
    params,
  })

export const getAdminPromptTemplate = (templateId: number) =>
  request<ApiResponse<PromptTemplateAdminDetail>>({
    url: `/prompt-templates/${templateId}`,
    method: 'GET',
  })

export const createAdminPromptTemplate = (body: PromptTemplateCreatePayload) =>
  request<ApiResponse<PromptTemplateAdminDetail>>({
    url: '/prompt-templates',
    method: 'POST',
    data: body,
  })

export const updateAdminPromptTemplate = (templateId: number, body: PromptTemplateUpdatePayload) =>
  request<ApiResponse<PromptTemplateAdminDetail>>({
    url: `/prompt-templates/${templateId}`,
    method: 'PUT',
    data: body,
  })

export const archiveAdminPromptTemplate = (templateId: number) =>
  request<ApiResponse<PromptTemplateAdminDetail>>({
    url: `/prompt-templates/${templateId}/archive`,
    method: 'POST',
  })

export const deleteAdminPromptTemplate = (templateId: number) =>
  request<ApiResponse<null>>({
    url: `/prompt-templates/${templateId}`,
    method: 'DELETE',
  })

export const listAdminPromptTemplateVersions = (templateId: number) =>
  request<ApiResponse<PromptTemplateAdminVersion[]>>({
    url: `/prompt-templates/${templateId}/versions`,
    method: 'GET',
  })

export const getAdminPromptTemplateVersion = (templateId: number, versionNo: number) =>
  request<ApiResponse<PromptTemplateAdminVersion>>({
    url: `/prompt-templates/${templateId}/versions/${versionNo}`,
    method: 'GET',
  })

export const createAdminPromptTemplateVersion = (templateId: number, body: PromptTemplateVersionPayload) =>
  request<ApiResponse<PromptTemplateAdminVersion>>({
    url: `/prompt-templates/${templateId}/versions`,
    method: 'POST',
    data: body,
  })

export const updateAdminPromptTemplateVersion = (
  templateId: number,
  versionNo: number,
  body: PromptTemplateVersionPayload,
) =>
  request<ApiResponse<PromptTemplateAdminVersion>>({
    url: `/prompt-templates/${templateId}/versions/${versionNo}`,
    method: 'PUT',
    data: body,
  })

export const deleteAdminPromptTemplateVersion = (templateId: number, versionNo: number) =>
  request<ApiResponse<null>>({
    url: `/prompt-templates/${templateId}/versions/${versionNo}`,
    method: 'DELETE',
  })

export const publishAdminPromptTemplateVersion = (templateId: number, versionNo: number) =>
  request<ApiResponse<PromptTemplateAdminDetail>>({
    url: `/prompt-templates/${templateId}/versions/${versionNo}/publish`,
    method: 'POST',
  })

export const loadDefaultWorkspace = async (): Promise<WorkspaceSummaryResponse | null> => {
  const response = await listWorkspaces({ pageNo: 1, pageSize: 20 })
  if (response.data.code !== 0) return null
  return response.data.data?.records?.[0] ?? null
}
