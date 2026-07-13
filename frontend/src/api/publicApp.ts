import type { AxiosRequestConfig } from 'axios'
import request from '@/request'
import type { PageResponse } from '@/api/workspace'
import type { PreviewTokenResponse } from '@/api/preview'
import type { PublicationDownloadAvailability } from '@/utils/marketplaceDownload'

interface ApiResponse<T> {
  code: number
  message: string
  data?: T
  requestId?: string
}

export interface PublicAppListItemResponse {
  publicationId: string
  slug: string
  publicTitle: string
  publicDescription?: string
  appType?: string
  publisherDisplayName?: string
  versionNo?: number
  allowPreview?: boolean
  allowDownload?: boolean
  downloadAvailability?: PublicationDownloadAvailability
  publishedAt?: string
  viewCount?: number
  downloadCount?: number
}

export interface PublicAppDetailResponse extends PublicAppListItemResponse {
  generationSource?: string
  updatedAt?: string
}

export interface PublicDownloadTokenResponse {
  downloadUrl: string
  expiresInSeconds: number
}

export const listPublicApps = (params: {
  pageNo?: number
  pageSize?: number
  keyword?: string
  appType?: string
  sort?: string
}) =>
  request<ApiResponse<PageResponse<PublicAppListItemResponse>>>('/public/apps', {
    method: 'GET',
    params,
  })

export const getPublicAppDetail = (slug: string) =>
  request<ApiResponse<PublicAppDetailResponse>>(`/public/apps/${slug}`, {
    method: 'GET',
    skipGlobalErrorToast: true,
  } as AxiosRequestConfig & { skipGlobalErrorToast?: boolean })

export const createPublicPreviewToken = (slug: string) =>
  request<ApiResponse<PreviewTokenResponse>>(`/public/apps/${slug}/preview-token`, {
    method: 'POST',
    skipGlobalErrorToast: true,
  } as AxiosRequestConfig & { skipGlobalErrorToast?: boolean })

export const recordPublicAppView = (slug: string) =>
  request<ApiResponse<{ counted: boolean; viewCount: number }>>(`/public/apps/${slug}/view`, {
    method: 'POST',
    skipGlobalErrorToast: true,
  } as AxiosRequestConfig & { skipGlobalErrorToast?: boolean })

export const createPublicDownloadToken = (slug: string) =>
  request<ApiResponse<PublicDownloadTokenResponse>>(`/public/apps/${slug}/download-token`, {
    method: 'POST',
    skipGlobalErrorToast: true,
  } as AxiosRequestConfig & { skipGlobalErrorToast?: boolean })
