import request from '@/request'
import type { IdLike, LongId } from '@/types/id'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId: string
}

export interface GenerationTaskCreateRequest {
  workspaceId: IdLike
  appId: IdLike
  taskType: string
  promptTemplateId?: number
  promptTemplateVersionId?: number
  promptTemplateVersionNo?: number
  templateVariables?: Record<string, string>
  requirement: string
  idempotencyKey?: string
}

export interface GenerationTaskCreateResponse {
  taskId: LongId
  workspaceId: LongId
  appId: LongId
  taskType: string
  taskStatus: string
  requestId: string
  queuedAt?: string
}

export interface GenerationTaskDetailResponse {
  id: LongId
  workspaceId: LongId
  appId: LongId
  taskType: string
  taskStatus: string
  errorCode?: string
  errorMessage?: string
  queuedAt?: string
  startedAt?: string
  finishedAt?: string
}

export interface PublicGenerationStreamEvent {
  eventId: string
  taskId: string
  type: string
  stage: string
  message: string
  timestamp?: string
  terminal: boolean
  data: Record<string, unknown>
}

/** @deprecated Use PublicGenerationStreamEvent from history/stream APIs */
export interface GenerationTaskEventResponse {
  id: LongId
  taskId: LongId
  eventType: string
  eventMessage: string
  eventPayloadJson?: string
  requestId?: string
  timestamp?: string
  createdAt?: string
}

export interface GenerationRecordResponse {
  id: LongId
  taskId: LongId
  promptTemplateVersionId?: LongId
  modelProviderId?: LongId
  modelName?: string
  inputSummary?: string
  outputSummary?: string
  tokenInput?: number
  tokenOutput?: number
  durationMs?: number
  createdAt?: string
}

export const createGenerationTask = (body: GenerationTaskCreateRequest) =>
  request<ApiResponse<GenerationTaskCreateResponse>>('/generation-tasks', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const getGenerationTask = (taskId: IdLike) =>
  request<ApiResponse<GenerationTaskDetailResponse>>(`/generation-tasks/${taskId}`, {
    method: 'GET',
  })

export const listGenerationTaskEvents = (taskId: IdLike, afterEventId?: string) =>
  request<ApiResponse<PublicGenerationStreamEvent[]>>(`/generation-tasks/${taskId}/events`, {
    method: 'GET',
    params: afterEventId ? { afterEventId } : undefined,
  })

export const cancelGenerationTask = (taskId: IdLike) =>
  request<ApiResponse<GenerationTaskDetailResponse>>(`/generation-tasks/${taskId}/cancel`, {
    method: 'POST',
  })

export const retryGenerationTask = (taskId: IdLike) =>
  request<ApiResponse<GenerationTaskCreateResponse>>(`/generation-tasks/${taskId}/retry`, {
    method: 'POST',
  })

export const listGenerationRecords = (appId: IdLike) =>
  request<ApiResponse<GenerationRecordResponse[]>>(`/apps/${appId}/generation-records`, {
    method: 'GET',
  })
