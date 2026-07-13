import type { GenerationConnectionState } from '@/pages/app/generationProgressViewModel'
import { compareTimelineEventId, isTerminalTaskStatus } from '@/pages/app/generationTaskObservation'
import { classifySseStreamError } from '@/utils/sseStream'

export type RecoveryInternalState =
  | 'IDLE'
  | 'CONNECTING'
  | 'LIVE'
  | 'RECONNECT_WAIT'
  | 'POLLING'
  | 'COMPLETED'
  | 'STOPPED'

export interface StreamRecoveryTimers {
  reconnectTimer: ReturnType<typeof setTimeout> | null
  pollTimer: ReturnType<typeof setInterval> | null
  activeStream: boolean
}

export interface StreamRecoverySession {
  epoch: number
  taskId: string
  state: RecoveryInternalState
  reconnectAttempt: number
  lastConsumedEventId?: string
  stopped: boolean
  terminal: boolean
  timers: StreamRecoveryTimers
}

const BACKOFF_SEQUENCE_MS = [1000, 2000, 4000, 8000, 15000]

export function isValidEventCursor(eventId?: string): boolean {
  return Boolean(eventId && /^\d+$/.test(eventId))
}

export function advanceEventCursor(current: string | undefined, incoming?: string): string | undefined {
  if (!isValidEventCursor(incoming)) {
    return current
  }
  if (!current) {
    return incoming
  }
  return compareTimelineEventId(current, incoming) < 0 ? incoming : current
}

export function resolveBackoffDelayMs(
  attempt: number,
  options: { jitterRatio?: number; random?: () => number } = {},
): number {
  const index = Math.max(0, Math.min(attempt, BACKOFF_SEQUENCE_MS.length - 1))
  const base = BACKOFF_SEQUENCE_MS[index] ?? 15000
  const jitterRatio = options.jitterRatio ?? 0
  if (jitterRatio <= 0) {
    return base
  }
  const random = options.random ?? Math.random
  const spread = base * jitterRatio
  const offset = (random() * 2 - 1) * spread
  return Math.max(0, Math.round(base + offset))
}

export function mapRecoveryToConnectionState(state: RecoveryInternalState): GenerationConnectionState {
  switch (state) {
    case 'CONNECTING':
      return 'CONNECTING'
    case 'LIVE':
      return 'LIVE'
    case 'RECONNECT_WAIT':
      return 'DISCONNECTED'
    case 'POLLING':
      return 'POLLING'
    case 'COMPLETED':
      return 'COMPLETED'
    case 'STOPPED':
      return 'DISCONNECTED'
    case 'IDLE':
    default:
      return 'CONNECTING'
  }
}

export function resolveRecoveryConnectionLabel(state: RecoveryInternalState): string | undefined {
  if (state === 'RECONNECT_WAIT') {
    return '正在恢复实时连接'
  }
  return undefined
}

export function shouldStartPollingOnFailure(state: RecoveryInternalState, retryable: boolean): boolean {
  return retryable && state !== 'COMPLETED' && state !== 'STOPPED'
}

export function shouldScheduleReconnect(error: unknown, session: StreamRecoverySession): boolean {
  if (session.stopped || session.terminal) {
    return false
  }
  const classification = classifySseStreamError(error)
  return classification.retryable
}

export function createStreamRecoverySession(taskId: string, epoch: number): StreamRecoverySession {
  return {
    epoch,
    taskId,
    state: 'IDLE',
    reconnectAttempt: 0,
    lastConsumedEventId: undefined,
    stopped: false,
    terminal: false,
    timers: {
      reconnectTimer: null,
      pollTimer: null,
      activeStream: false,
    },
  }
}

export function markStreamRecoveryTerminal(session: StreamRecoverySession): StreamRecoverySession {
  return {
    ...session,
    terminal: true,
    state: 'COMPLETED',
    reconnectAttempt: 0,
    timers: {
      reconnectTimer: null,
      pollTimer: null,
      activeStream: false,
    },
  }
}

export function markStreamRecoveryStopped(session: StreamRecoverySession): StreamRecoverySession {
  return {
    ...session,
    stopped: true,
    state: 'STOPPED',
    reconnectAttempt: 0,
    timers: {
      reconnectTimer: null,
      pollTimer: null,
      activeStream: false,
    },
  }
}

export function isStaleObservationSession(session: StreamRecoverySession, epoch: number, taskId: string): boolean {
  return session.stopped || session.epoch !== epoch || session.taskId !== taskId
}

export function isRecoveryDeadZone(session: StreamRecoverySession): boolean {
  if (session.stopped || session.terminal) {
    return false
  }
  const { timers } = session
  return !timers.activeStream && timers.reconnectTimer === null && timers.pollTimer === null
}

export function hasActiveRecoveryMechanism(session: StreamRecoverySession): boolean {
  if (session.stopped || session.terminal) {
    return false
  }
  const { timers } = session
  return timers.activeStream || timers.reconnectTimer !== null || timers.pollTimer !== null
}

export function shouldSkipTransientFailureToast(retryable: boolean): boolean {
  return retryable
}

export function shouldFinalizeTerminalFromPolling(taskStatus?: string): boolean {
  return isTerminalTaskStatus(taskStatus)
}

export function resolveReconnectErrorMatrix(error: unknown): {
  status?: number
  retry: boolean
  connectionState: GenerationConnectionState
  polling: boolean
} {
  const classification = classifySseStreamError(error)
  const connectionState: GenerationConnectionState = classification.retryable ? 'DISCONNECTED' : 'DISCONNECTED'
  return {
    status: classification.status,
    retry: classification.retryable,
    connectionState,
    polling: classification.retryable,
  }
}
