import request from '@/request'
import type { PageResponse } from '@/api/workspace'
import type { IdLike, LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface AiAppListItemResponse {
  id: LongId
  workspaceId: LongId
  name: string
  description?: string
  coverUrl?: string
  appType: string
  status: string
  visibility: string
  currentVersionId?: LongId
  latestTaskId?: LongId
  createdAt?: string
  updatedAt?: string
  currentVersionNo?: number
  latestGenerationSource?: string
  generatedFileCount?: number
  latestExportStatus?: string
  displayStatus?: string
  publicationStatus?: string
  publicationSlug?: string
  publicationId?: LongId
}

export interface AiAppDetailResponse extends AiAppListItemResponse {}

export interface AiAppCreateRequest {
  workspaceId: IdLike
  name: string
  description?: string
  appType: string
}

export interface AiAppUpdateRequest {
  name?: string
  description?: string
  coverUrl?: string
  visibility?: string
}

export interface AiAppQueryRequest {
  workspaceId?: number
  keyword?: string
  status?: string
  appType?: string
  pageNo?: number
  pageSize?: number
}

export const createAiApp = (body: AiAppCreateRequest) =>
  request<ApiResponse<AiAppDetailResponse>>('/apps', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const listAiApps = (params: AiAppQueryRequest) =>
  request<ApiResponse<PageResponse<AiAppListItemResponse>>>('/apps', {
    method: 'GET',
    params,
  })

export const getAiApp = (appId: IdLike) =>
  request<ApiResponse<AiAppDetailResponse>>(`/apps/${appId}`, {
    method: 'GET',
  })

export const updateAiApp = (appId: IdLike, body: AiAppUpdateRequest) =>
  request<ApiResponse<AiAppDetailResponse>>(`/apps/${appId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const archiveAiApp = (appId: IdLike) =>
  request<ApiResponse<AiAppDetailResponse>>(`/apps/${appId}/archive`, {
    method: 'POST',
  })

export const deleteAiApp = (appId: IdLike) =>
  request<ApiResponse<void>>(`/apps/${appId}`, {
    method: 'DELETE',
  })
