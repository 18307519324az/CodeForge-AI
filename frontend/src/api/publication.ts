import request from '@/request'
import type { IdLike } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface AppPublicationResponse {
  publicationId: string
  appId: string
  versionId: string
  versionNo: number
  slug: string
  status: string
  publicTitle: string
  publicDescription?: string
  allowPreview: boolean
  allowDownload: boolean
  publishedAt?: string
  unpublishedAt?: string
  viewCount?: number
  downloadCount?: number
}

export interface AppPublicationCreateRequest {
  versionId: string
  publicTitle: string
  publicDescription?: string
  allowPreview: boolean
  allowDownload: boolean
}

export interface AppPublicationUpdateRequest {
  versionId?: string
  publicTitle?: string
  publicDescription?: string
  allowPreview?: boolean
  allowDownload?: boolean
}

export const publishApp = (appId: IdLike, body: AppPublicationCreateRequest) =>
  request<ApiResponse<AppPublicationResponse>>(`/apps/${appId}/publications`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    data: body,
  })

export const getCurrentPublication = (appId: IdLike) =>
  request<ApiResponse<AppPublicationResponse>>(`/apps/${appId}/publications/current`, {
    method: 'GET',
    skipGlobalErrorToast: true,
  } as never)

export const updatePublication = (
  appId: IdLike,
  publicationId: IdLike,
  body: AppPublicationUpdateRequest,
) =>
  request<ApiResponse<AppPublicationResponse>>(`/apps/${appId}/publications/${publicationId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    data: body,
  })

export const unpublishApp = (appId: IdLike, publicationId: IdLike) =>
  request<ApiResponse<AppPublicationResponse>>(`/apps/${appId}/publications/${publicationId}/unpublish`, {
    method: 'POST',
  })
