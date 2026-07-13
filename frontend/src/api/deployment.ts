import request from '@/request'
import type { IdLike, LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface DeploymentCreateRequest {
  appId: IdLike
  appVersionId: IdLike
  environmentCode: string
  deployTarget: string
  runtimeConfigJson?: string
}

export interface DeploymentCreateResponse {
  id: LongId
  appId: LongId
  appVersionId: LongId
  environmentCode: string
  deployTarget: string
  deployStatus: string
  requestId: string
  createdAt?: string
}

export interface DeploymentDetailResponse {
  id: LongId
  appId: LongId
  appVersionId: LongId
  environmentCode: string
  deployTarget: string
  deployStatus: string
  runtimeConfigJson?: string
  requestId: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface DeploymentLogResponse {
  id: LongId
  deploymentJobId: LongId
  logLevel: string
  logMessage: string
  logTime?: string
}

export const createDeployment = (body: DeploymentCreateRequest) =>
  request<ApiResponse<DeploymentCreateResponse>>('/deployments', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const getDeployment = (deploymentJobId: IdLike) =>
  request<ApiResponse<DeploymentDetailResponse>>(`/deployments/${deploymentJobId}`, {
    method: 'GET',
  })

export const getDeploymentLogs = (deploymentJobId: IdLike) =>
  request<ApiResponse<DeploymentLogResponse[]>>(`/deployments/${deploymentJobId}/logs`, {
    method: 'GET',
  })
