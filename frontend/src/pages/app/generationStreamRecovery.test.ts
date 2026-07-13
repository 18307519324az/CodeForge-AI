import { describe, expect, it, vi } from 'vitest'
import {
  advanceEventCursor,
  isRecoveryDeadZone,
  isValidEventCursor,
  mapRecoveryToConnectionState,
  resolveBackoffDelayMs,
  resolveRecoveryConnectionLabel,
  shouldScheduleReconnect,
  shouldSkipTransientFailureToast,
  createStreamRecoverySession,
} from '@/pages/app/generationStreamRecovery'
import {
  GenerationStreamRecoveryController,
  type StreamRecoveryPollResult,
} from '@/pages/app/generationStreamRecoveryController'
import { mergeTimelineEvent, type TimelineEvent } from '@/pages/app/generationTaskObservation'
import { classifySseStreamError, SseStreamError } from '@/utils/sseStream'

type TimerEntry = {
  id: number
  fn: () => void
  delay: number
  type: 'timeout' | 'interval'
}

type ConnectBehavior =
  | 'ok'
  | 'ok-without-activity'
  | 'http200-then-read-fail'
  | Error

function createHarness(options: {
  connectResults?: ConnectBehavior[]
  pollResults?: StreamRecoveryPollResult[]
} = {}) {
  let connectIndex = 0
  let pollIndex = 0
  let timerId = 0
  const timers: TimerEntry[] = []
  const connectCalls: Array<{ taskId: string; lastEventId?: string; at: number }> = []
  const connectionStates: Array<{ state: string; label?: string }> = []
  const mergedEvents: TimelineEvent[] = []
  const metrics = {
    terminalCalls: 0,
    nonRetryableCalls: 0,
  }

  const setTimeoutFn = vi.fn((fn: () => void, delay?: number) => {
    timerId += 1
    timers.push({ id: timerId, fn, delay: delay ?? 0, type: 'timeout' })
    return timerId
  }) as unknown as typeof setTimeout

  const clearTimeoutFn = vi.fn((id: number) => {
    const index = timers.findIndex((timer) => timer.id === id && timer.type === 'timeout')
    if (index >= 0) {
      timers.splice(index, 1)
    }
  }) as unknown as typeof clearTimeout

  const setIntervalFn = vi.fn((fn: () => void, delay?: number) => {
    timerId += 1
    timers.push({ id: timerId, fn, delay: delay ?? 0, type: 'interval' })
    return timerId
  }) as unknown as typeof setInterval

  const clearIntervalFn = vi.fn((id: number) => {
    const index = timers.findIndex((timer) => timer.id === id && timer.type === 'interval')
    if (index >= 0) {
      timers.splice(index, 1)
    }
  }) as unknown as typeof clearInterval

  const controller = new GenerationStreamRecoveryController(
    {
      onConnectionStateChange: (state, labelOverride) => {
        connectionStates.push({ state, label: labelOverride })
      },
      onEventsMerged: (incoming) => {
        for (const event of incoming) {
          mergedEvents.push(event)
        }
      },
      onTerminal: async () => {
        metrics.terminalCalls += 1
      },
      onNonRetryableFailure: () => {
        metrics.nonRetryableCalls += 1
      },
    },
    {
      connectStream: async (streamOptions) => {
        connectCalls.push({
          taskId: streamOptions.taskId,
          lastEventId: streamOptions.lastEventId,
          at: Date.now(),
        })
        const result = options.connectResults?.[connectIndex] ?? 'ok'
        connectIndex += 1
        if (result === 'ok') {
          streamOptions.onConnected()
          streamOptions.onActivity?.()
          streamOptions.onEvent({
            id: '701',
            type: 'TASK_STARTED',
            data: {
              eventId: '701',
              taskId: streamOptions.taskId,
              type: 'TASK_STARTED',
              message: 'started',
            } as Record<string, unknown>,
          })
          return
        }
        if (result === 'ok-without-activity') {
          streamOptions.onConnected()
          return
        }
        if (result === 'http200-then-read-fail') {
          streamOptions.onConnected()
          throw new SseStreamError('SSE read failure', undefined, true)
        }
        if (result instanceof Error) {
          throw result
        }
        throw new Error('missing connect result')
      },
      pollTask: async (_taskId, afterEventId) => {
        const result = options.pollResults?.[pollIndex] ?? { events: [], terminal: false }
        pollIndex += 1
        return result
      },
      random: () => 0.5,
      setTimeoutFn,
      clearTimeoutFn,
      setIntervalFn,
      clearIntervalFn,
      pollIntervalMs: 2000,
    },
  )

  const runDueTimeouts = () => {
    const due = timers.filter((timer) => timer.type === 'timeout')
    for (const timer of due) {
      const index = timers.indexOf(timer)
      if (index >= 0) {
        timers.splice(index, 1)
      }
      timer.fn()
    }
  }

  const runDueIntervals = () => {
    const due = timers.filter((timer) => timer.type === 'interval')
    for (const timer of due) {
      timer.fn()
    }
  }

  return {
    controller,
    connectCalls,
    connectionStates,
    mergedEvents,
    timers,
    metrics,
    runDueTimeouts,
    runDueIntervals,
  }
}

describe('StreamReconnectAfterNetworkFailureTest', () => {
  it('schedules reconnect after transient network failure', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91')
    expect(harness.connectCalls).toHaveLength(1)
    expect(harness.controller.hasReconnectTimer()).toBe(true)
    expect(harness.controller.hasPollingTimer()).toBe(true)

    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()

    expect(harness.connectCalls).toHaveLength(2)
  })
})

describe('StreamReconnectUsesLastEventIdTest', () => {
  it('passes last consumed event id on reconnect', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91', '700')
    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.connectCalls[0]?.lastEventId).toBe('700')
    expect(harness.connectCalls[1]?.lastEventId).toBe('700')
    expect(harness.controller.getLastConsumedEventId()).toBe('701')
  })
})

describe('StreamReconnectBackoffSequenceTest', () => {
  it('uses bounded exponential backoff sequence', () => {
    expect(resolveBackoffDelayMs(0, { jitterRatio: 0 })).toBe(1000)
    expect(resolveBackoffDelayMs(1, { jitterRatio: 0 })).toBe(2000)
    expect(resolveBackoffDelayMs(2, { jitterRatio: 0 })).toBe(4000)
    expect(resolveBackoffDelayMs(3, { jitterRatio: 0 })).toBe(8000)
  })
})

describe('StreamReconnectBackoffCapsAt15sTest', () => {
  it('caps reconnect delay at 15 seconds', () => {
    expect(resolveBackoffDelayMs(4, { jitterRatio: 0 })).toBe(15000)
    expect(resolveBackoffDelayMs(99, { jitterRatio: 0 })).toBe(15000)
  })
})

describe('StreamReconnectResetsBackoffAfterSuccessTest', () => {
  it('resets reconnect attempt only after stream activity', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok',
      ],
    })

    await harness.controller.start('91')
    expect(harness.controller.getReconnectAttempt()).toBe(1)

    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.controller.getReconnectAttempt()).toBe(0)
    expect(harness.controller.hasReconnectTimer()).toBe(false)
    expect(harness.connectionStates.some((entry) => entry.state === 'LIVE')).toBe(true)
  })
})

describe('Stream401DoesNotRetryForeverTest', () => {
  it('does not schedule reconnect on 401', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('auth', 401, false)],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasReconnectTimer()).toBe(false)
    expect(harness.controller.hasPollingTimer()).toBe(false)
    expect(harness.metrics.nonRetryableCalls).toBe(1)
  })
})

describe('Stream403DoesNotRetryTest', () => {
  it('does not schedule reconnect on 403', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('forbidden', 403, false)],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasReconnectTimer()).toBe(false)
    expect(harness.metrics.nonRetryableCalls).toBe(1)
  })
})

describe('Stream404DoesNotRetryTest', () => {
  it('does not schedule reconnect on 404', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('missing', 404, false)],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasReconnectTimer()).toBe(false)
  })
})

describe('Stream5xxRetriesTest', () => {
  it('retries on retryable 5xx', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('server', 503, true), 'ok'],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasReconnectTimer()).toBe(true)
  })
})

describe('Stream429RetriesTest', () => {
  it('retries on 429', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('rate limit', 429, true), 'ok'],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasReconnectTimer()).toBe(true)
  })
})

describe('PollingStartsDuringReconnectTest', () => {
  it('starts polling while waiting to reconnect', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasPollingTimer()).toBe(true)
    expect(harness.connectionStates.some((entry) => entry.state === 'POLLING')).toBe(true)
  })
})

describe('PollingStopsWhenStreamRecoversTest', () => {
  it('stops polling after stream reconnect succeeds', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasPollingTimer()).toBe(true)

    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.controller.hasPollingTimer()).toBe(false)
    expect(harness.connectionStates.some((entry) => entry.state === 'LIVE')).toBe(true)
  })
})

describe('PollingAndSseMergeByEventIdTest', () => {
  it('deduplicates polling and stream events by event id', () => {
    let events: TimelineEvent[] = []
    const pollEvent = { id: '701', taskId: '91', eventType: 'TASK_STARTED', eventMessage: 'started' }
    const streamEvent = { id: '701', taskId: '91', eventType: 'TASK_STARTED', eventMessage: 'started-replay' }

    events = mergeTimelineEvent(events, pollEvent)
    events = mergeTimelineEvent(events, streamEvent)

    expect(events).toHaveLength(1)
    expect(events[0]?.eventMessage).toBe('started-replay')
  })
})

describe('SameTaskHasSingleActiveStreamTest', () => {
  it('keeps only one active stream connect call per attempt', async () => {
    const harness = createHarness({ connectResults: ['ok'] })
    await harness.controller.start('91')
    expect(harness.connectCalls).toHaveLength(1)
  })
})

describe('SameTaskHasSingleReconnectTimerTest', () => {
  it('keeps only one reconnect timer per task', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        new SseStreamError('network', undefined, true),
        'ok',
      ],
    })

    await harness.controller.start('91')
    const timeoutCount = harness.timers.filter((timer) => timer.type === 'timeout').length
    expect(timeoutCount).toBe(1)
  })
})

describe('SameTaskHasSinglePollingTimerTest', () => {
  it('keeps only one polling timer per task', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true)],
    })

    await harness.controller.start('91')
    const intervalCount = harness.timers.filter((timer) => timer.type === 'interval').length
    expect(intervalCount).toBe(1)
  })
})

describe('TaskSwitchCancelsOldRecoveryTest', () => {
  it('cancels old task reconnect when switching tasks', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok',
        'ok',
      ],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasReconnectTimer()).toBe(true)

    await harness.controller.start('92')
    expect(harness.controller.isObservingTask('91')).toBe(false)
    expect(harness.connectCalls.at(-1)?.taskId).toBe('92')
  })
})

describe('UnmountCancelsReconnectTest', () => {
  it('cancels reconnect timer on stop', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true)],
    })

    await harness.controller.start('91')
    harness.controller.stop()
    expect(harness.controller.hasReconnectTimer()).toBe(false)
    expect(harness.controller.hasPollingTimer()).toBe(false)
  })
})

describe('TerminalCancelsReconnectTest', () => {
  it('cancels reconnect timer on terminal finalize', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true)],
    })

    await harness.controller.start('91')
    harness.controller.finalizeTerminal()
    expect(harness.controller.hasReconnectTimer()).toBe(false)
    expect(harness.controller.hasPollingTimer()).toBe(false)
  })
})

describe('PollingTerminalCancelsReconnectTest', () => {
  it('cancels reconnect when polling discovers terminal task', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true)],
      pollResults: [{ events: [], taskStatus: 'SUCCESS', terminal: true }],
    })

    await harness.controller.start('91')
    await Promise.resolve()
    await Promise.resolve()

    expect(harness.metrics.terminalCalls).toBe(1)
    expect(harness.controller.hasReconnectTimer()).toBe(false)
  })
})

describe('ReconnectDoesNotDuplicateEventsTest', () => {
  it('does not duplicate replayed events after reconnect', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91', '700')
    harness.runDueTimeouts()
    await Promise.resolve()

    const ids = harness.mergedEvents.map((event) => event.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})

describe('ReconnectDoesNotLoseEventsTest', () => {
  it('advances cursor without losing valid events', () => {
    expect(advanceEventCursor('700', '701')).toBe('701')
    expect(advanceEventCursor('701', '700')).toBe('701')
    expect(advanceEventCursor('701', '702')).toBe('702')
  })
})

describe('StaleObservationCannotReopenOldTaskTest', () => {
  it('prevents stale reconnect timer from reopening old task stream', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok',
        'ok',
      ],
    })

    await harness.controller.start('91')
    await harness.controller.start('92')
    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.connectCalls.at(-1)?.taskId).toBe('92')
  })
})

describe('TransientFailureDoesNotToastStormTest', () => {
  it('suppresses toast for transient retryable failures', () => {
    expect(shouldSkipTransientFailureToast(true)).toBe(true)
    expect(shouldSkipTransientFailureToast(false)).toBe(false)
  })
})

describe('ConnectionStateRecoveryTest', () => {
  it('maps reconnect wait to disconnected presentation with recovery label', () => {
    expect(mapRecoveryToConnectionState('RECONNECT_WAIT')).toBe('DISCONNECTED')
    expect(resolveRecoveryConnectionLabel('RECONNECT_WAIT')).toBe('正在恢复实时连接')
    expect(mapRecoveryToConnectionState('LIVE')).toBe('LIVE')
  })
})

describe('LargeSnowflakeEventIdStaysStringTest', () => {
  it('keeps snowflake event ids as numeric strings without Number conversion', () => {
    const snowflake = '9223372036854775807'
    expect(isValidEventCursor(snowflake)).toBe(true)
    expect(advanceEventCursor(undefined, snowflake)).toBe(snowflake)
    expect(advanceEventCursor('9223372036854775806', snowflake)).toBe(snowflake)
  })
})

describe('ReconnectErrorMatrixTest', () => {
  it('classifies reconnect errors consistently', () => {
    expect(classifySseStreamError(new SseStreamError('network', undefined, true)).retryable).toBe(true)
    expect(classifySseStreamError(new SseStreamError('auth', 401, false)).retryable).toBe(false)
    expect(classifySseStreamError(new SseStreamError('forbidden', 403, false)).retryable).toBe(false)
    expect(classifySseStreamError(new SseStreamError('missing', 404, false)).retryable).toBe(false)
    expect(classifySseStreamError(new SseStreamError('server', 503, true)).retryable).toBe(true)
    expect(classifySseStreamError(new SseStreamError('rate', 429, true)).retryable).toBe(true)
  })
})

describe('CursorMalformedFramePolicyTest', () => {
  it('does not advance cursor for invalid event ids', () => {
    expect(advanceEventCursor('101', 'not-a-number')).toBe('101')
    expect(advanceEventCursor('101', undefined)).toBe('101')
    expect(advanceEventCursor('101', '103')).toBe('103')
  })
})

describe('shouldScheduleReconnectTest', () => {
  it('does not reconnect after terminal session', () => {
    const session = createStreamRecoverySession('91', 1)
    session.terminal = true
    expect(shouldScheduleReconnect(new SseStreamError('network', undefined, true), session)).toBe(false)
  })
})

describe('Http200ThenImmediateReadFailureKeepsPollingTest', () => {
  it('keeps polling active when HTTP 200 is followed by immediate read failure', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'http200-then-read-fail',
      ],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasPollingTimer()).toBe(true)

    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()

    expect(harness.controller.hasPollingTimer()).toBe(true)
    expect(harness.controller.hasReconnectTimer()).toBe(true)
    expect(harness.connectionStates.some((entry) => entry.state === 'LIVE')).toBe(false)
  })
})

describe('Http200AloneDoesNotStopPollingTest', () => {
  it('does not stop polling on HTTP 200 alone', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok-without-activity',
      ],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasPollingTimer()).toBe(true)

    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.controller.hasPollingTimer()).toBe(true)
    expect(harness.connectionStates.some((entry) => entry.state === 'LIVE')).toBe(false)
  })
})

describe('FirstActivityStopsPollingTest', () => {
  it('stops polling after first stream activity', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91')
    expect(harness.controller.hasPollingTimer()).toBe(true)

    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.controller.hasPollingTimer()).toBe(false)
    expect(harness.connectionStates.some((entry) => entry.state === 'LIVE')).toBe(true)
  })
})

describe('ImmediateReadFailureSchedulesBackoffTest', () => {
  it('schedules backoff after immediate read failure following HTTP 200', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'http200-then-read-fail',
      ],
    })

    await harness.controller.start('91')
    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()

    const reconnectTimer = harness.timers.find((timer) => timer.type === 'timeout')
    expect(reconnectTimer?.delay).toBe(2000)
    expect(harness.controller.getReconnectAttempt()).toBe(2)
  })
})

describe('ReconnectActuallyWaitsForBackoffTest', () => {
  it('does not reconnect before backoff delay elapses', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok',
      ],
    })

    await harness.controller.start('91')
    expect(harness.connectCalls).toHaveLength(1)

    const reconnectTimer = harness.timers.find((timer) => timer.type === 'timeout')
    expect(reconnectTimer?.delay).toBe(1000)
    expect(harness.connectCalls).toHaveLength(1)

    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()

    expect(harness.connectCalls).toHaveLength(2)
  })
})

describe('NoImmediateRecursiveReconnectTest', () => {
  it('does not schedule duplicate reconnect timers for one failure', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true), 'ok'],
    })

    await harness.controller.start('91')
    const timeoutCount = harness.timers.filter((timer) => timer.type === 'timeout').length
    expect(timeoutCount).toBe(1)
    expect(harness.connectCalls).toHaveLength(1)
  })
})

describe('RunningRecoveryNeverEntersDeadZoneTest', () => {
  it('keeps polling or reconnect active after recoverable failure', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'http200-then-read-fail',
      ],
    })

    await harness.controller.start('91')
    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()

    expect(
      harness.controller.hasPollingTimer() ||
        harness.controller.hasReconnectTimer() ||
        harness.controller.hasActiveStream(),
    ).toBe(true)
  })
})

describe('PollingContinuesAcrossReconnectFailuresTest', () => {
  it('continues polling across repeated reconnect failures', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'http200-then-read-fail',
        'http200-then-read-fail',
      ],
    })

    await harness.controller.start('91')
    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()
    expect(harness.controller.hasPollingTimer()).toBe(true)

    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()
    expect(harness.controller.hasPollingTimer()).toBe(true)
  })
})

describe('ReconnectReadsLatestCursorAtExecutionTimeTest', () => {
  it('reads the latest cursor when reconnect timer fires', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok-without-activity',
      ],
      pollResults: [
        {
          events: [
            {
              id: '702',
              taskId: '91',
              eventType: 'MODEL_DELTA',
              eventMessage: 'delta',
              createdAt: '2026-01-01T00:00:00.000Z',
              terminal: false,
              data: {},
            },
          ],
          terminal: false,
        },
      ],
    })

    await harness.controller.start('91', '700')
    harness.runDueIntervals()
    await Promise.resolve()

    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.connectCalls.at(-1)?.lastEventId).toBe('702')
  })
})

describe('PollingMergeAdvancesReconnectCursorTest', () => {
  it('advances reconnect cursor after polling merge', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok-without-activity',
      ],
      pollResults: [
        {
          events: [
            {
              id: '703',
              taskId: '91',
              eventType: 'MODEL_DELTA',
              eventMessage: 'delta',
              createdAt: '2026-01-01T00:00:00.000Z',
              terminal: false,
              data: {},
            },
          ],
          terminal: false,
        },
      ],
    })

    await harness.controller.start('91', '701')
    harness.runDueIntervals()
    await Promise.resolve()
    expect(harness.controller.getLastConsumedEventId()).toBe('703')

    harness.runDueTimeouts()
    await Promise.resolve()
    expect(harness.connectCalls.at(-1)?.lastEventId).toBe('703')
  })
})

describe('BackoffResetsOnlyAfterActivityTest', () => {
  it('does not reset backoff on HTTP 200 without activity', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok-without-activity',
      ],
    })

    await harness.controller.start('91')
    expect(harness.controller.getReconnectAttempt()).toBe(1)

    harness.runDueTimeouts()
    await Promise.resolve()

    expect(harness.controller.getReconnectAttempt()).toBe(1)
  })
})

describe('ReadFailureBeforeActivityDoesNotResetBackoffTest', () => {
  it('does not reset backoff when read fails before activity', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'http200-then-read-fail',
      ],
    })

    await harness.controller.start('91')
    harness.runDueTimeouts()
    await Promise.resolve()
    await Promise.resolve()

    expect(harness.controller.getReconnectAttempt()).toBe(2)
  })
})

describe('PollingSingleRequestFailureDoesNotStopLoopTest', () => {
  it('continues polling after a single poll request failure', async () => {
    let pollCalls = 0
    let timerId = 0
    const timers: TimerEntry[] = []
    const controller = new GenerationStreamRecoveryController(
      {
        onConnectionStateChange: () => {},
        onEventsMerged: () => {},
        onTerminal: async () => {},
      },
      {
        connectStream: async () => {
          throw new SseStreamError('network', undefined, true)
        },
        pollTask: async () => {
          pollCalls += 1
          if (pollCalls === 1) {
            throw new Error('poll failed once')
          }
          return { events: [], terminal: false }
        },
        random: () => 0.5,
        setTimeoutFn: vi.fn((fn: () => void, delay?: number) => {
          timerId += 1
          timers.push({ id: timerId, fn, delay: delay ?? 0, type: 'timeout' })
          return timerId
        }) as unknown as typeof setTimeout,
        clearTimeoutFn: vi.fn() as unknown as typeof clearTimeout,
        setIntervalFn: vi.fn((fn: () => void, delay?: number) => {
          timerId += 1
          timers.push({ id: timerId, fn, delay: delay ?? 0, type: 'interval' })
          return timerId
        }) as unknown as typeof setInterval,
        clearIntervalFn: vi.fn() as unknown as typeof clearInterval,
        pollIntervalMs: 2000,
      },
    )

    await controller.start('91')
    const interval = timers.find((timer) => timer.type === 'interval')
    interval?.fn()
    await Promise.resolve()

    expect(pollCalls).toBeGreaterThanOrEqual(2)
    expect(controller.hasPollingTimer()).toBe(true)
  })
})

describe('TransientFailuresContinueAt15sCapTest', () => {
  it('caps reconnect delay at 15 seconds', async () => {
    const harness = createHarness({
      connectResults: Array.from({ length: 8 }, () => new SseStreamError('network', undefined, true)),
    })

    await harness.controller.start('91')
    expect(harness.timers.filter((timer) => timer.type === 'timeout').at(-1)?.delay).toBe(1000)

    for (let attempt = 0; attempt < 5; attempt += 1) {
      harness.runDueTimeouts()
      await Promise.resolve()
      await Promise.resolve()
    }

    expect(harness.timers.filter((timer) => timer.type === 'timeout').at(-1)?.delay).toBe(15000)
  })
})

describe('TerminalStillStopsRecoveryTest', () => {
  it('still stops recovery on terminal finalize', async () => {
    const harness = createHarness({
      connectResults: [new SseStreamError('network', undefined, true)],
    })

    await harness.controller.start('91')
    harness.controller.finalizeTerminal()
    expect(harness.controller.hasReconnectTimer()).toBe(false)
    expect(harness.controller.hasPollingTimer()).toBe(false)
  })
})

describe('TaskSwitchStillCancelsOldRecoveryTest', () => {
  it('still cancels old task recovery when switching tasks', async () => {
    const harness = createHarness({
      connectResults: [
        new SseStreamError('network', undefined, true),
        'ok',
        'ok',
      ],
    })

    await harness.controller.start('91')
    await harness.controller.start('92')
    expect(harness.controller.isObservingTask('91')).toBe(false)
  })
})
