import adminRequest from '@/utils/adminRequest'
import type { PageResponse } from '@/api/workspace'
import type { IdLike, LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface MetricSummaryResponse {
  statDate: string | null
  requestCount: number
  successCount: number
  failedCount: number
  successRate: string
  tokenInput: number
  tokenOutput: number
  avgDurationMs: number
  metricScope: string
  dataAsOf: string | null
  generatedAt: string
  freshnessStatus: 'FRESH' | 'STALE' | 'EMPTY'
  staleAfterSeconds: number
}

export interface MetricRefreshResponse {
  metricScope: string
  refreshedAt: string | null
  rebuiltDays: number
  requestCount: number
  successCount: number
  failedCount: number
  freshnessStatus: 'FRESH' | 'STALE' | 'EMPTY'
}

export interface AuditLogResponse {
  id: LongId
  workspaceId?: LongId
  operatorUserId?: LongId
  actionType: string
  targetType: string
  targetId?: string
  requestId?: string
  createdAt?: string
}

export interface QuotaUsageLogResponse {
  id: LongId
  quotaId?: LongId
  taskId?: LongId
  usageType: string
  requestCount?: number
  tokenCount?: number
  costAmount?: string
  createdAt?: string
}

export interface AdminUserListItemResponse {
  id: LongId
  account: string
  displayName?: string
  email?: string
  status: string
  platformRoles: string[]
  createdAt?: string
  lastLoginAt?: string
}

export interface ModelProviderResponse {
  id: LongId
  providerCode: string
  providerName: string
  baseUrl?: string
  authMode: string
  secretRef?: string
  status: string
  priority?: number
  defaultModel?: string
  credentialSource?: string
  configured?: boolean
  apiKeyConfigured?: boolean
  maskedHint?: string
  createdAt?: string
  updatedAt?: string
}

export interface ModelProviderUpdateRequest {
  providerName: string
  baseUrl?: string
  authMode: string
  defaultModel?: string
  priority?: number
  status?: string
  credentialSource?: string
}

export interface ProviderCredentialUpsertRequest {
  apiKey: string
}

export interface ProviderCredentialResponse {
  configured: boolean
  source: string
  maskedHint?: string
}

export interface AiRoutingConfigResponse {
  mode: 'AUTO' | 'PIN'
  pinnedProviderCode?: string
  effectiveCandidates: string[]
  adminPersisted: boolean
}

export interface AiRoutingConfigUpdateRequest {
  mode: 'AUTO' | 'PIN'
  providerCode?: string
}

export interface ModelProviderStatusUpdateRequest {
  status: 'ACTIVE' | 'DISABLED'
}

export interface ProviderHealthCheckResponse {
  providerId: LongId
  providerCode: string
  healthy: boolean
  message?: string
  checkedAt?: string
}

export interface AdminAppListItemResponse {
  id: LongId
  workspaceId: LongId
  name: string
  description?: string
  appType: string
  status: string
  visibility?: string
  currentVersionId?: LongId
  latestTaskId?: LongId
  createdAt?: string
  updatedAt?: string
}

export interface ModelProviderCreateRequest {
  providerCode: string
  providerName: string
  baseUrl?: string
  authMode: string
}

export interface ModelCallLogResponse {
  id: LongId
  taskId?: LongId
  appId?: LongId
  providerId?: LongId
  providerCode?: string
  modelName?: string
  requestId?: string
  status: string
  inputTokens?: number
  outputTokens?: number
  durationMs?: number
  fallbackUsed?: boolean
  generationSource?: string
  errorMessage?: string
  createdAt?: string
}

export interface AdminPageQuery {
  pageNo?: number
  pageSize?: number
  keyword?: string
}

export const listAdminApps = (params: AdminPageQuery) =>
  adminRequest<ApiResponse<PageResponse<AdminAppListItemResponse>>>('/admin/apps', {
    method: 'GET',
    params,
  })

export const getMetricSummary = () =>
  adminRequest<ApiResponse<MetricSummaryResponse>>('/admin/metrics/summary', {
    method: 'GET',
  })

export const refreshMetricSummary = () =>
  adminRequest<ApiResponse<MetricRefreshResponse>>('/admin/metrics/refresh', {
    method: 'POST',
  })

export const listAuditLogs = (params: { pageNo?: number; pageSize?: number }) =>
  adminRequest<ApiResponse<PageResponse<AuditLogResponse>>>('/admin/audit-logs', {
    method: 'GET',
    params,
  })

export const listQuotaUsageLogs = () =>
  adminRequest<ApiResponse<QuotaUsageLogResponse[]>>('/admin/quotas/usage-logs', {
    method: 'GET',
  })

export const listAdminUsers = (params: AdminPageQuery) =>
  adminRequest<ApiResponse<PageResponse<AdminUserListItemResponse>>>('/admin/users', {
    method: 'GET',
    params,
  })

export const listModelProviders = () =>
  adminRequest<ApiResponse<ModelProviderResponse[]>>('/admin/model-providers', {
    method: 'GET',
  })

export const createModelProvider = (body: ModelProviderCreateRequest) =>
  adminRequest<ApiResponse<ModelProviderResponse>>('/admin/model-providers', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const updateModelProvider = (providerId: IdLike, body: ModelProviderUpdateRequest) =>
  adminRequest<ApiResponse<ModelProviderResponse>>(`/admin/model-providers/${providerId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const upsertProviderCredential = (providerId: IdLike, body: ProviderCredentialUpsertRequest) =>
  adminRequest<ApiResponse<ProviderCredentialResponse>>(`/admin/model-providers/${providerId}/credential`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const deleteProviderCredential = (providerId: IdLike) =>
  adminRequest<ApiResponse<ProviderCredentialResponse>>(`/admin/model-providers/${providerId}/credential`, {
    method: 'DELETE',
  })

export const getAiRouting = () =>
  adminRequest<ApiResponse<AiRoutingConfigResponse>>('/admin/ai-routing', {
    method: 'GET',
  })

export const updateAiRouting = (body: AiRoutingConfigUpdateRequest) =>
  adminRequest<ApiResponse<AiRoutingConfigResponse>>('/admin/ai-routing', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const updateModelProviderStatus = (
  providerId: IdLike,
  body: ModelProviderStatusUpdateRequest,
) =>
  adminRequest<ApiResponse<ModelProviderResponse>>(`/admin/model-providers/${providerId}/status`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const healthCheckModelProvider = (providerId: IdLike) =>
  adminRequest<ApiResponse<ProviderHealthCheckResponse>>(
    `/admin/model-providers/${providerId}/health-check`,
    {
      method: 'POST',
    },
  )

export const listModelCallLogs = (params: AdminPageQuery) =>
  adminRequest<ApiResponse<PageResponse<ModelCallLogResponse>>>('/admin/model-call-logs', {
    method: 'GET',
    params,
  })
