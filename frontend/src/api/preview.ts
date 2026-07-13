import type { AxiosRequestConfig } from 'axios'
import request from '@/request'
import type { IdLike } from '@/types/id'
import { stringifyId } from '@/utils/stringifyId'

interface ApiResponse<T> {
  code: number
  message: string
  data?: T
  requestId?: string
}

export interface PreviewTokenResponse {
  previewUrl: string
  expiresInSeconds: number
}

export const createAppVersionPreviewToken = (appId: IdLike, versionId: IdLike) => {
  const safeAppId = stringifyId(appId)
  const safeVersionId = stringifyId(versionId)
  if (!safeAppId || !safeVersionId) {
    return Promise.reject(new Error('预览参数无效'))
  }
  return request<ApiResponse<PreviewTokenResponse>>(
    `/apps/${safeAppId}/versions/${safeVersionId}/preview-token`,
    {
      method: 'POST',
      skipGlobalErrorToast: true,
    } as AxiosRequestConfig & { skipGlobalErrorToast?: boolean },
  )
}
