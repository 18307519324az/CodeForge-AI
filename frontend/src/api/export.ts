import request from '@/request'
import type { IdLike, LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface ExportPackageCreateRequest {
  appId: IdLike
  appVersionId: IdLike
  packageType: string
}

export interface ExportPackageCreateResponse {
  id: LongId
  appId: LongId
  appVersionId: LongId
  versionNo: number
  packageType: string
  status: string
  fileName: string
  createdAt?: string
}

export interface ExportPackageListItemResponse {
  id: LongId
  appId: LongId
  appVersionId: LongId
  packageType: string
  status: string
  fileName: string
  createdAt?: string
}

export const createExportPackage = (body: ExportPackageCreateRequest) =>
  request<ApiResponse<ExportPackageCreateResponse>>('/export-packages', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const listExportPackages = (appId: IdLike) =>
  request<ApiResponse<ExportPackageListItemResponse[]>>(`/apps/${appId}/export-packages`, {
    method: 'GET',
  })

export async function downloadExportPackageFile(packageId: IdLike): Promise<void> {
  const { API_BASE_URL } = await import('@/config/env')
  const { getAccessToken } = await import('@/auth/token')
  const token = getAccessToken()
  const response = await fetch(`${API_BASE_URL}/export-packages/${packageId}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    credentials: 'include',
  })
  if (!response.ok) {
    throw new Error('下载失败')
  }
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition')
  const filename =
    disposition?.match(/filename="(.+)"/)?.[1] ?? `export-${packageId}.zip`
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
