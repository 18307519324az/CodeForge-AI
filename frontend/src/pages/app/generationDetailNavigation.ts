import type { LocationQuery } from 'vue-router'
import type { LongId } from '@/types/id'

export function buildGenerationDetailRoute(appId: string, taskId: LongId): string {
  return `/apps/${String(appId)}/generate?taskId=${String(taskId)}`
}

export function resolveQueryTaskId(query: LocationQuery): string {
  const rawTaskId = query.taskId
  if (Array.isArray(rawTaskId)) {
    return rawTaskId[0] ? String(rawTaskId[0]) : ''
  }
  return rawTaskId != null ? String(rawTaskId) : ''
}
