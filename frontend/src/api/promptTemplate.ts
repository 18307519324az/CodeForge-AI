import request from '@/request'
import type {
  PromptTemplatePublishedVersion,
  PromptTemplateUserDetail,
  PromptTemplateUserListItem,
  PublishedPromptTemplateQuery,
} from '@/utils/promptTemplateTypes'

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

export const listPublishedTemplates = (params?: PublishedPromptTemplateQuery) =>
  request<ApiResponse<PageResponse<PromptTemplateUserListItem>>>({
    url: '/prompt-templates/published',
    method: 'GET',
    params,
  })

export const getPublishedTemplate = (templateId: number) =>
  request<ApiResponse<PromptTemplateUserDetail>>({
    url: `/prompt-templates/published/${templateId}`,
    method: 'GET',
  })

export const listPublishedTemplateVersions = (templateId: number) =>
  request<ApiResponse<PromptTemplatePublishedVersion[]>>({
    url: `/prompt-templates/published/${templateId}/versions`,
    method: 'GET',
  })
