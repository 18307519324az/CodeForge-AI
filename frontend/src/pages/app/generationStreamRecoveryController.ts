import type { GenerationConnectionState } from '@/pages/app/generationProgressViewModel'
import type { TimelineEvent } from '@/pages/app/generationTaskObservation'
import type { SseStreamEvent } from '@/utils/sseStream'
import { classifySseStreamError } from '@/utils/sseStream'
import {
  advanceEventCursor,
  createStreamRecoverySession,
  isRecoveryDeadZone,
  isStaleObservationSession,
  isValidEventCursor,
  mapRecoveryToConnectionState,
  markStreamRecoveryStopped,
  markStreamRecoveryTerminal,
  resolveBackoffDelayMs,
  resolveRecoveryConnectionLabel,
  shouldSkipTransientFailureToast,
  type StreamRecoverySession,
} from '@/pages/app/generationStreamRecovery'

export interface StreamConnectOptions {
  taskId: string
  lastEventId?: string
  signal: AbortSignal
  onConnected: () => void
  onEvent: (event: SseStreamEvent<Record<string, unknown>>) => void
  onTerminal: (event: SseStreamEvent<Record<string, unknown>>) => void
  onMalformedFrame?: (segment: string) => void
  onActivity?: () => void
}

export interface StreamRecoveryPollResult {
  events: TimelineEvent[]
  taskStatus?: string
  terminal: boolean
}

export interface GenerationStreamRecoveryCallbacks {
  onConnectionStateChange: (state: GenerationConnectionState, labelOverride?: string) => void
  onEventsMerged: (events: TimelineEvent[], source: 'stream' | 'poll') => void
  onTerminal: () => Promise<void>
  onNonRetryableFailure?: (error: unknown) => void
}

export interface GenerationStreamRecoveryDeps {
  connectStream: (options: StreamConnectOptions) => Promise<void>
  pollTask: (taskId: string, afterEventId?: string) => Promise<StreamRecoveryPollResult>
  random?: () => number
  setTimeoutFn?: typeof setTimeout
  clearTimeoutFn?: typeof clearTimeout
  setIntervalFn?: typeof setInterval
  clearIntervalFn?: typeof clearInterval
  pollIntervalMs?: number
}

export class GenerationStreamRecoveryController {
  private epoch = 0

  private session: StreamRecoverySession | null = null

  private streamController: AbortController | null = null

  private streamAttemptId = 0

  private readonly random: () => number

  private readonly setTimeoutFn: typeof setTimeout

  private readonly clearTimeoutFn: typeof clearTimeout

  private readonly setIntervalFn: typeof setInterval

  private readonly clearIntervalFn: typeof clearInterval

  private readonly pollIntervalMs: number

  constructor(
    private readonly callbacks: GenerationStreamRecoveryCallbacks,
    private readonly deps: GenerationStreamRecoveryDeps,
  ) {
    this.random = deps.random ?? Math.random
    this.setTimeoutFn = deps.setTimeoutFn ?? setTimeout
    this.clearTimeoutFn = deps.clearTimeoutFn ?? clearTimeout
    this.setIntervalFn = deps.setIntervalFn ?? setInterval
    this.clearIntervalFn = deps.clearIntervalFn ?? clearInterval
    this.pollIntervalMs = deps.pollIntervalMs ?? 2000
  }

  getLastConsumedEventId(): string | undefined {
    return this.session?.lastConsumedEventId
  }

  getReconnectAttempt(): number {
    return this.session?.reconnectAttempt ?? 0
  }

  hasActiveStream(): boolean {
    return Boolean(this.session?.timers.activeStream)
  }

  hasReconnectTimer(): boolean {
    return Boolean(this.session?.timers.reconnectTimer)
  }

  hasPollingTimer(): boolean {
    return Boolean(this.session?.timers.pollTimer)
  }

  isObservingTask(taskId: string): boolean {
    if (!this.session || this.session.stopped || this.session.terminal) {
      return false
    }
    if (this.session.taskId !== taskId) {
      return false
    }
    return (
      this.session.timers.activeStream ||
      this.session.timers.reconnectTimer !== null ||
      this.session.timers.pollTimer !== null
    )
  }

  async start(taskId: string, initialCursor?: string): Promise<void> {
    this.cancelReconnectTimer()
    this.stopPollingTimer()
    this.abortStream()
    if (this.session) {
      this.session = markStreamRecoveryStopped(this.session)
    }
    this.epoch += 1
    const epoch = this.epoch
    this.session = createStreamRecoverySession(taskId, epoch)
    if (isValidEventCursor(initialCursor)) {
      this.session.lastConsumedEventId = initialCursor
    }
    await this.connectStream(epoch)
  }

  stop(): void {
    this.epoch += 1
    this.cancelReconnectTimer()
    this.stopPollingTimer()
    this.abortStream()
    if (this.session) {
      this.session = markStreamRecoveryStopped(this.session)
    }
  }

  finalizeTerminal(): void {
    this.cancelReconnectTimer()
    this.stopPollingTimer()
    this.abortStream()
    if (this.session) {
      this.session = markStreamRecoveryTerminal(this.session)
    }
    this.callbacks.onConnectionStateChange('COMPLETED')
  }

  private updateConnectionState(state: StreamRecoverySession['state']): void {
    if (!this.session) {
      return
    }
    this.session.state = state
    const labelOverride = resolveRecoveryConnectionLabel(state)
    this.callbacks.onConnectionStateChange(mapRecoveryToConnectionState(state), labelOverride)
  }

  private isStale(epoch: number, taskId: string): boolean {
    if (!this.session) {
      return true
    }
    return isStaleObservationSession(this.session, epoch, taskId)
  }

  private cancelReconnectTimer(): void {
    if (!this.session?.timers.reconnectTimer) {
      return
    }
    this.clearTimeoutFn(this.session.timers.reconnectTimer)
    this.session.timers.reconnectTimer = null
  }

  private stopPollingTimer(): void {
    if (!this.session?.timers.pollTimer) {
      return
    }
    this.clearIntervalFn(this.session.timers.pollTimer)
    this.session.timers.pollTimer = null
  }

  private abortStream(): void {
    if (this.streamController) {
      this.streamController.abort()
      this.streamController = null
    }
    if (this.session) {
      this.session.timers.activeStream = false
    }
  }

  private mergeIncomingEvents(incoming: TimelineEvent[], source: 'stream' | 'poll'): void {
    if (!this.session || incoming.length === 0) {
      return
    }
    this.callbacks.onEventsMerged(incoming, source)
    for (const event of incoming) {
      if (isValidEventCursor(event.id)) {
        this.session.lastConsumedEventId = advanceEventCursor(this.session.lastConsumedEventId, event.id)
      }
    }
  }

  private markStreamHealthy(epoch: number, taskId: string): void {
    if (!this.session || this.isStale(epoch, taskId)) {
      return
    }
    this.session.reconnectAttempt = 0
    this.cancelReconnectTimer()
    this.stopPollingTimer()
    this.updateConnectionState('LIVE')
  }

  private resolveConnectionStateWhileWaitingForActivity(): void {
    if (!this.session || this.session.terminal) {
      return
    }
    if (this.session.timers.pollTimer) {
      this.updateConnectionState('POLLING')
      return
    }
    if (this.session.timers.reconnectTimer) {
      this.updateConnectionState('RECONNECT_WAIT')
      return
    }
    this.updateConnectionState('CONNECTING')
  }

  private ensureRecoverableSession(epoch: number, taskId: string): void {
    if (!this.session || this.isStale(epoch, taskId) || this.session.terminal || this.session.stopped) {
      return
    }
    if (!isRecoveryDeadZone(this.session)) {
      return
    }
    void this.startPolling(epoch, taskId)
    this.scheduleReconnect(epoch, taskId)
  }

  private async connectStream(epoch: number): Promise<void> {
    if (!this.session || this.isStale(epoch, this.session.taskId)) {
      return
    }

    const taskId = this.session.taskId
    const attemptId = ++this.streamAttemptId
    this.abortStream()
    this.updateConnectionState('CONNECTING')

    const accessSignal = new AbortController()
    this.streamController = accessSignal
    this.session.timers.activeStream = true

    let connected = false

    try {
      const lastEventId = this.session.lastConsumedEventId
      await this.deps.connectStream({
        taskId,
        lastEventId,
        signal: accessSignal.signal,
        onConnected: () => {
          if (this.isStale(epoch, taskId) || attemptId !== this.streamAttemptId) {
            return
          }
          connected = true
          this.resolveConnectionStateWhileWaitingForActivity()
        },
        onActivity: () => {
          if (this.isStale(epoch, taskId) || attemptId !== this.streamAttemptId) {
            return
          }
          this.markStreamHealthy(epoch, taskId)
        },
        onEvent: (streamEvent) => {
          if (this.isStale(epoch, taskId) || attemptId !== this.streamAttemptId) {
            return
          }
          const timelineEvent = this.toTimelineEvent(taskId, streamEvent)
          this.mergeIncomingEvents([timelineEvent], 'stream')
        },
        onTerminal: () => {
          if (this.isStale(epoch, taskId) || attemptId !== this.streamAttemptId) {
            return
          }
          void this.callbacks.onTerminal()
        },
      })

      if (this.isStale(epoch, taskId) || attemptId !== this.streamAttemptId) {
        return
      }

      if (connected && this.session && !this.session.terminal) {
        this.session.timers.activeStream = false
      }
    } catch (error: unknown) {
      if (attemptId !== this.streamAttemptId) {
        return
      }
      if (!this.session || this.session.stopped || this.session.taskId !== taskId) {
        return
      }
      this.session.timers.activeStream = false

      const classification = classifySseStreamError(error)
      if (classification.kind === 'aborted') {
        return
      }

      await this.handleStreamFailure(epoch, taskId, error)
    } finally {
      if (this.session && this.session.taskId === taskId && attemptId === this.streamAttemptId) {
        this.session.timers.activeStream = false
      }
      if (this.streamController === accessSignal) {
        this.streamController = null
      }
    }
  }

  private async handleStreamFailure(epoch: number, taskId: string, error: unknown): Promise<void> {
    if (!this.session || this.session.stopped || this.session.taskId !== taskId) {
      return
    }

    const classification = classifySseStreamError(error)
    if (classification.kind === 'aborted') {
      return
    }

    if (!classification.retryable) {
      this.updateConnectionState('STOPPED')
      this.callbacks.onNonRetryableFailure?.(error)
      return
    }

    await this.startPolling(epoch, taskId)
    if (!this.session || this.session.terminal) {
      return
    }
    this.scheduleReconnect(epoch, taskId)
    this.ensureRecoverableSession(epoch, taskId)
  }

  private scheduleReconnect(epoch: number, taskId: string): void {
    if (!this.session || this.isStale(epoch, taskId) || this.session.terminal) {
      return
    }

    if (this.session.timers.reconnectTimer) {
      return
    }

    this.updateConnectionState('RECONNECT_WAIT')

    const attempt = this.session.reconnectAttempt
    const delayMs = resolveBackoffDelayMs(attempt, { jitterRatio: 0.1, random: this.random })
    this.session.reconnectAttempt += 1

    this.session.timers.reconnectTimer = this.setTimeoutFn(() => {
      if (!this.session) {
        return
      }
      this.session.timers.reconnectTimer = null
      if (this.isStale(epoch, taskId) || this.session.terminal) {
        return
      }
      void this.connectStream(epoch)
    }, delayMs)
  }

  private async startPolling(epoch: number, taskId: string): Promise<void> {
    if (!this.session || this.isStale(epoch, taskId) || this.session.terminal) {
      return
    }

    if (this.session.timers.pollTimer) {
      this.updateConnectionState('POLLING')
      return
    }

    this.updateConnectionState('POLLING')

    const pollOnce = async () => {
      if (!this.session || this.isStale(epoch, taskId) || this.session.terminal) {
        return
      }
      try {
        const result = await this.deps.pollTask(taskId, this.session.lastConsumedEventId)
        if (this.isStale(epoch, taskId) || !this.session || this.session.terminal) {
          return
        }
        this.mergeIncomingEvents(result.events, 'poll')
        if (result.terminal) {
          this.cancelReconnectTimer()
          this.stopPollingTimer()
          await this.callbacks.onTerminal()
          if (this.session) {
            this.session = markStreamRecoveryTerminal(this.session)
          }
        }
      } catch {
        // keep polling as fallback
      }
    }

    await pollOnce()
    if (!this.session || this.isStale(epoch, taskId) || this.session.terminal) {
      return
    }
    this.session.timers.pollTimer = this.setIntervalFn(() => {
      void pollOnce()
    }, this.pollIntervalMs)
  }

  private toTimelineEvent(taskId: string, streamEvent: SseStreamEvent<Record<string, unknown>>): TimelineEvent {
    const payload = streamEvent.data
    const eventType = streamEvent.type || String(payload?.type || 'MESSAGE')
    return {
      id: streamEvent.id || String(payload?.eventId || Date.now()),
      taskId: String(payload?.taskId ?? taskId),
      eventType,
      eventMessage: String(payload?.message || eventType),
      createdAt: String(payload?.timestamp ?? new Date().toISOString()),
      terminal: payload?.terminal === true,
      data: (payload?.data as Record<string, unknown> | undefined) ?? {},
    }
  }
}
