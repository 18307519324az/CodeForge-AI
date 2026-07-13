import request from '@/request'
import type { LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface PageResponse<T> {
  records: T[]
  pageNo: number
  pageSize: number
  total: number
}

export interface WorkspaceSummaryResponse {
  id: LongId
  name: string
  description?: string
  ownerUserId: LongId
  status: string
  planCode: string
  memberRole: string
}

export interface WorkspaceDetailResponse extends WorkspaceSummaryResponse {}

export interface WorkspaceCreateRequest {
  name: string
  description?: string
}

export interface WorkspaceQueryRequest {
  pageNo?: number
  pageSize?: number
}

export const listWorkspaces = (params: WorkspaceQueryRequest) =>
  request<ApiResponse<PageResponse<WorkspaceSummaryResponse>>>('/workspaces', {
    method: 'GET',
    params,
  })

export const createWorkspace = (body: WorkspaceCreateRequest) =>
  request<ApiResponse<WorkspaceDetailResponse>>('/workspaces', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

/** Idempotent: returns existing workspace or creates default for current user. */
export const getOrCreateDefaultWorkspace = () =>
  request<ApiResponse<WorkspaceDetailResponse>>('/workspaces/default', {
    method: 'POST',
  })
