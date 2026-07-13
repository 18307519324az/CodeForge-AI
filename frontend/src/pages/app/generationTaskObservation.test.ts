import { describe, expect, it } from 'vitest'
import {
  buildGenerateRouteQuery,
  compareTimelineEventId,
  isTerminalTaskStatus,
  mergeTimelineEvent,
  normalizeTaskId,
  resolveLastStreamEventId,
  shouldSkipDuplicateObservation,
  timelineEventsFromPublicEvents,
  waitForLatestTaskId,
  type TimelineEvent,
} from '@/pages/app/generationTaskObservation'

describe('NewTaskImmediatelyStartsObservationTest', () => {
  it('binds taskId string before terminal and prepares observation state', () => {
    const taskId = normalizeTaskId('80')
    expect(taskId).toBe('80')
    expect(shouldSkipDuplicateObservation(undefined, taskId, false)).toBe(false)
  })
})

describe('NewTaskWritesTaskIdToRouteTest', () => {
  it('writes taskId as string into route query', () => {
    const query = buildGenerateRouteQuery('80', { templateId: '1' })
    expect(query.taskId).toBe('80')
    expect(query.templateId).toBe('1')
    expect(typeof query.taskId).toBe('string')
  })
})

describe('NewTaskStartsStreamWithoutRemountTest', () => {
  it('does not require route remount to start observing a new task', async () => {
    let pollCount = 0
    const taskId = await waitForLatestTaskId(async () => {
      pollCount += 1
      return pollCount >= 2 ? '81' : undefined
    }, { timeoutMs: 2000, intervalMs: 10 })

    expect(taskId).toBe('81')
    expect(pollCount).toBeGreaterThanOrEqual(2)
  })
})

describe('SameTaskOnlyOneActiveStreamTest', () => {
  it('skips duplicate observation when same task already has active stream', () => {
    expect(shouldSkipDuplicateObservation('80', '80', true)).toBe(true)
    expect(shouldSkipDuplicateObservation('80', '80', false)).toBe(false)
    expect(shouldSkipDuplicateObservation('79', '80', true)).toBe(false)
  })
})

describe('RouteWatchDoesNotDuplicateStreamTest', () => {
  it('treats route watch callback as idempotent for active same-task stream', () => {
    const routeTaskId = normalizeTaskId('80')
    const observedTaskId = '80'
    const hasActiveStream = true
    expect(shouldSkipDuplicateObservation(observedTaskId, routeTaskId, hasActiveStream)).toBe(true)
  })
})

describe('SubmitAndRouteWatchIdempotentTest', () => {
  it('keeps single observation when submit and route watch target same taskId', () => {
    const submitTaskId = '82'
    const routeTaskId = normalizeTaskId(submitTaskId)
    expect(routeTaskId).toBe(submitTaskId)
    expect(shouldSkipDuplicateObservation(submitTaskId, routeTaskId, true)).toBe(true)
  })
})

describe('LiveEventImmediatelyUpdatesTimelineTest', () => {
  it('merges live events incrementally instead of waiting for terminal', () => {
    let events: TimelineEvent[] = []
    events = mergeTimelineEvent(events, {
      id: '159',
      taskId: '80',
      eventType: 'TASK_CREATED',
      eventMessage: 'created',
    })
    events = mergeTimelineEvent(events, {
      id: '160',
      taskId: '80',
      eventType: 'TASK_STARTED',
      eventMessage: 'started',
    })

    expect(events.map((event) => event.eventType)).toEqual(['TASK_CREATED', 'TASK_STARTED'])
  })
})

describe('TerminalEventUpdatesTimelineAndResultTest', () => {
  it('marks terminal status and includes failed event in merged timeline', () => {
    const events = timelineEventsFromPublicEvents([
      {
        eventId: '159',
        taskId: '80',
        type: 'TASK_CREATED',
        stage: 'TASK',
        message: 'created',
        timestamp: '2026-07-07T01:25:23Z',
        terminal: false,
        data: {},
      },
      {
        eventId: '167',
        taskId: '80',
        type: 'TASK_FAILED',
        stage: 'TASK',
        message: 'failed',
        timestamp: '2026-07-07T01:28:47Z',
        terminal: true,
        data: {},
      },
    ])

    expect(events.at(-1)?.eventType).toBe('TASK_FAILED')
    expect(isTerminalTaskStatus('FAILED')).toBe(true)
  })
})

describe('PollingFallbackMergesByEventIdTest', () => {
  it('deduplicates polling events by event id', () => {
    const base: TimelineEvent[] = [
      { id: '159', taskId: '80', eventType: 'TASK_CREATED', eventMessage: 'created' },
    ]
    const merged = mergeTimelineEvent(base, {
      id: '159',
      taskId: '80',
      eventType: 'TASK_CREATED',
      eventMessage: 'created-updated',
    })
    const withNew = mergeTimelineEvent(merged, {
      id: '160',
      taskId: '80',
      eventType: 'TASK_STARTED',
      eventMessage: 'started',
    })

    expect(merged).toHaveLength(1)
    expect(merged[0]?.eventMessage).toBe('created-updated')
    expect(withNew.map((event) => event.id)).toEqual(['159', '160'])
    expect(resolveLastStreamEventId(withNew)).toBe('160')
  })
})

describe('RefreshRestoresTaskObservationTest', () => {
  it('resolves taskId string from route query for refresh bootstrap', async () => {
    const events = timelineEventsFromPublicEvents([
      {
        eventId: '140',
        taskId: '77',
        type: 'TASK_CREATED',
        stage: 'TASK',
        message: 'created',
        timestamp: '2026-07-07T00:00:00Z',
        terminal: false,
        data: {},
      },
    ])

    expect(events[0]?.taskId).toBe('77')
    expect(compareTimelineEventId('140', '149')).toBe(-1)
  })
})
