import type { PublicGenerationStreamEvent } from '@/api/task'
import type { LongId } from '@/types/id'

export interface TimelineEvent {
  id: string
  taskId: string
  eventType: string
  eventMessage: string
  createdAt?: string
  terminal?: boolean
  data?: Record<string, unknown>
}

export const TERMINAL_TASK_STATUSES = ['SUCCESS', 'FAILED', 'CANCELLED'] as const

export function normalizeTaskId(taskId: LongId): string {
  return String(taskId)
}

export function shouldSkipDuplicateObservation(
  observedTaskId: string | undefined,
  requestedTaskId: string,
  hasActiveStream: boolean,
): boolean {
  return observedTaskId === requestedTaskId && hasActiveStream
}

export function compareTimelineEventId(leftId?: string, rightId?: string): number {
  const left = BigInt(leftId || '0')
  const right = BigInt(rightId || '0')
  if (left === right) {
    return 0
  }
  return left > right ? 1 : -1
}

export function mergeTimelineEvent(events: TimelineEvent[], incoming: TimelineEvent): TimelineEvent[] {
  if (!incoming.id) {
    return [...events, incoming]
  }

  const existingIndex = events.findIndex((item) => item.id === incoming.id)
  if (existingIndex >= 0) {
    const next = [...events]
    next[existingIndex] = incoming
    return next
  }

  return [...events, incoming].sort((left, right) => compareTimelineEventId(left.id, right.id))
}

export function timelineEventsFromPublicEvents(events: PublicGenerationStreamEvent[]): TimelineEvent[] {
  return events
    .map((event) => ({
      id: event.eventId,
      taskId: event.taskId,
      eventType: event.type,
      eventMessage: event.message,
      createdAt: event.timestamp,
      terminal: event.terminal,
      data: event.data,
    }))
    .sort((left, right) => compareTimelineEventId(left.id, right.id))
}

export function buildGenerateRouteQuery(
  taskId: string,
  currentQuery: Record<string, unknown> = {},
): Record<string, unknown> & { taskId: string } {
  return {
    ...currentQuery,
    taskId: normalizeTaskId(taskId),
  }
}

export function isTerminalTaskStatus(status?: string): boolean {
  return TERMINAL_TASK_STATUSES.includes((status || '') as (typeof TERMINAL_TASK_STATUSES)[number])
}

export async function waitForLatestTaskId(
  fetchLatestTaskId: () => Promise<LongId | undefined>,
  options: { timeoutMs?: number; intervalMs?: number } = {},
): Promise<string | undefined> {
  const timeoutMs = options.timeoutMs ?? 30_000
  const intervalMs = options.intervalMs ?? 500
  const deadline = Date.now() + timeoutMs

  while (Date.now() < deadline) {
    const latestTaskId = await fetchLatestTaskId()
    if (latestTaskId) {
      return normalizeTaskId(latestTaskId)
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }

  return undefined
}

export function resolveLastStreamEventId(events: TimelineEvent[]): string | undefined {
  const lastEvent = [...events]
    .filter((event) => event.id && /^\d+$/.test(event.id))
    .sort((left, right) => compareTimelineEventId(left.id, right.id))
    .at(-1)

  return lastEvent?.id
}
