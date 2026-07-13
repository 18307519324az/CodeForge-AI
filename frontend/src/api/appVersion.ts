import request from '@/request'
import type { PageResponse } from '@/api/workspace'
import type { IdLike, LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface AppVersionListItemResponse {
  id: LongId
  versionNo: number
  versionSource: string
  sourceTaskId?: LongId
  changeSummary?: string
  status: string
  publishedAt?: string
  createdAt?: string
}

export interface AppVersionFileResponse {
  id: LongId
  appVersionId: LongId
  filePath: string
  fileName: string
  fileType: string
  contentHash?: string
  fileSize?: number
}

export interface AppVersionFileContentResponse {
  versionId: LongId
  filePath: string
  fileName: string
  fileType: string
  content: string
}

export const listAppVersions = (appId: IdLike, params: { pageNo?: number; pageSize?: number }) =>
  request<ApiResponse<PageResponse<AppVersionListItemResponse>>>(`/apps/${appId}/versions`, {
    method: 'GET',
    params,
  })

export const listAppVersionFiles = (appId: IdLike, versionId: IdLike) =>
  request<ApiResponse<AppVersionFileResponse[]>>(`/apps/${appId}/versions/${versionId}/files`, {
    method: 'GET',
  })

export const getAppVersionFileContent = (appId: IdLike, versionId: IdLike, filePath: string) =>
  request<ApiResponse<AppVersionFileContentResponse>>(
    `/apps/${appId}/versions/${versionId}/files/content`,
    {
      method: 'GET',
      params: { filePath },
    },
  )
