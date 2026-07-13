import { describe, expect, it } from 'vitest'
import {
  buildGenerationMilestones,
  buildGenerationProgressViewModel,
  buildMilestoneSignature,
  formatElapsedDuration,
  formatReceivedChars,
  isGenerationInProgress,
  isMilestoneTimelineEventType,
  resolveElapsedMs,
  resolveLatestModelProgress,
  resolveSafeFailureMessage,
  resolveTaskEndAtMs,
  resolveTaskStartedAt,
  resolveWaitingHint,
  resolveWaitingHints,
  shouldAutoScrollTimeline,
  shouldRenderTimelineRow,
} from '@/pages/app/generationProgressViewModel'
import type { TimelineEvent } from '@/pages/app/generationTaskObservation'
import type { GenerationTaskDetailResponse } from '@/api/task'

function timelineEvent(
  id: string,
  eventType: string,
  createdAt: string,
  data: Record<string, unknown> = {},
): TimelineEvent {
  return {
    id,
    taskId: '90',
    eventType,
    eventMessage: eventType,
    createdAt,
    data,
  }
}

const TASK_90_EVENTS = [
  timelineEvent('1', 'TASK_CREATED', '2026-07-07T19:28:50'),
  timelineEvent('2', 'TASK_STARTED', '2026-07-07T19:28:50'),
  timelineEvent('3', 'PROMPT_RENDERED', '2026-07-07T19:28:50'),
  timelineEvent('4', 'MODEL_CALL_STARTED', '2026-07-07T19:28:50'),
  timelineEvent('999', 'TASK_FAILED', '2026-07-07T19:33:33'),
]

const RUNNING_AI_MODEL_EVENTS = [
  timelineEvent('1', 'TASK_CREATED', '2026-07-07T19:28:50'),
  timelineEvent('2', 'TASK_STARTED', '2026-07-07T19:28:50'),
  timelineEvent('3', 'PROMPT_RENDERED', '2026-07-07T19:28:50'),
  timelineEvent('4', 'MODEL_CALL_STARTED', '2026-07-07T19:28:50'),
]

function buildModelDeltaEvents(count: number, attempt = 1, startId = 600): TimelineEvent[] {
  return Array.from({ length: count }, (_, index) =>
    timelineEvent(String(startId + index), 'MODEL_DELTA', `2026-07-07T19:29:${String(index % 60).padStart(2, '0')}`, {
      attempt,
      receivedChars: (index + 1) * 4,
      chunkCount: index + 1,
      elapsedMs: (index + 1) * 800,
    }),
  )
}

describe('GenerationMilestoneTimelineTest', () => {
  it('renders business milestones instead of raw event rows', () => {
    const events = [
      timelineEvent('1', 'TASK_CREATED', '2026-07-07T19:28:40'),
      timelineEvent('2', 'TASK_STARTED', '2026-07-07T19:28:41'),
      timelineEvent('3', 'PROMPT_RENDERED', '2026-07-07T19:28:42'),
      timelineEvent('4', 'MODEL_CALL_STARTED', '2026-07-07T19:28:50'),
    ]

    const milestones = buildGenerationMilestones(events, 'RUNNING')
    expect(milestones.map((item) => item.label)).toEqual([
      '任务已创建',
      '开始生成应用',
      '生成需求已准备',
      'AI 正在生成项目内容',
      '整理生成文件',
      '创建应用版本',
    ])
    expect(milestones.filter((item) => item.status === 'completed')).toHaveLength(3)
    expect(milestones.find((item) => item.key === 'AI_MODEL')?.status).toBe('active')
  })
})

describe('ModelDeltaDoesNotCreateTimelineRowTest', () => {
  it('does not treat MODEL_DELTA as timeline row event', () => {
    expect(shouldRenderTimelineRow('MODEL_DELTA')).toBe(false)
    expect(isMilestoneTimelineEventType('MODEL_DELTA')).toBe(false)
  })
})

describe('ModelDeltaLatestSnapshotTest', () => {
  it('keeps only the latest snapshot for the current attempt', () => {
    const events = [
      ...buildModelDeltaEvents(3, 1, 100),
      timelineEvent('200', 'MODEL_DELTA', '2026-07-07T19:30:01', {
        attempt: 2,
        receivedChars: 4,
        chunkCount: 1,
        elapsedMs: 1000,
      }),
      timelineEvent('201', 'MODEL_DELTA', '2026-07-07T19:30:02', {
        attempt: 2,
        receivedChars: 40,
        chunkCount: 10,
        elapsedMs: 2000,
      }),
    ]

    const snapshot = resolveLatestModelProgress(events)
    expect(snapshot).toEqual({
      attempt: 2,
      receivedChars: 40,
      chunkCount: 10,
      elapsedMs: 2000,
      lastUpdatedAt: '2026-07-07T19:30:02',
    })
  })
})

describe('ModelDelta338EventsSingleProgressBlockTest', () => {
  it('collapses 338 MODEL_DELTA events into one latest snapshot', () => {
    const events = [
      timelineEvent('10', 'MODEL_CALL_STARTED', '2026-07-07T19:28:50'),
      ...buildModelDeltaEvents(338, 1, 100),
    ]

    const snapshot = resolveLatestModelProgress(events)
    expect(events.filter((event) => event.eventType === 'MODEL_DELTA')).toHaveLength(338)
    expect(snapshot?.receivedChars).toBe(338 * 4)
    expect(snapshot?.attempt).toBe(1)
  })
})

describe('GenerationElapsedFromTaskStartedAtTest', () => {
  it('uses task.startedAt before falling back to timeline events', () => {
    const task = {
      id: '90',
      taskStatus: 'RUNNING',
      startedAt: '2026-07-07T19:28:41.000Z',
    } as GenerationTaskDetailResponse

    const startedAt = resolveTaskStartedAt(task, [
      timelineEvent('1', 'TASK_CREATED', '2026-07-07T19:28:40.000Z'),
    ])

    expect(startedAt).toBe('2026-07-07T19:28:41.000Z')
    expect(resolveElapsedMs(startedAt, Date.parse('2026-07-07T19:30:00.000Z'))).toBe(79_000)
    expect(formatElapsedDuration(79_000)).toBe('01:19')
  })
})

describe('GenerationElapsedRefreshRecoveryTest', () => {
  it('preserves elapsed time from persisted startedAt after refresh', () => {
    const startedAt = '2026-07-07T19:28:40.000Z'
    const viewModel = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'RUNNING',
        startedAt,
      } as GenerationTaskDetailResponse,
      events: [
        timelineEvent('1', 'TASK_CREATED', startedAt),
        timelineEvent('2', 'TASK_STARTED', '2026-07-07T19:28:41.000Z'),
        timelineEvent('3', 'MODEL_CALL_STARTED', '2026-07-07T19:28:50.000Z'),
      ],
      connectionState: 'SYNCING',
      nowMs: Date.parse('2026-07-07T19:31:03.000Z'),
    })

    expect(viewModel.startedAt).toBe(startedAt)
    expect(viewModel.elapsedMs).toBe(143_000)
  })
})

describe('Generation30SecondHintTest', () => {
  it('shows only the 30 second waiting hint during active model generation', () => {
    const hint = resolveWaitingHint({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:29:25'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hint).toContain('复杂应用通常需要一些时间')
    expect(resolveWaitingHints({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:29:25'),
      activeStageKey: 'AI_MODEL',
    })).toHaveLength(1)
  })
})

describe('Generation60SecondHintTest', () => {
  it('shows only the 60 second waiting hint and replaces the 30 second hint', () => {
    const hint = resolveWaitingHint({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:29:55'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hint).toContain('无需重复提交')
    expect(hint).not.toContain('复杂应用通常需要一些时间')
  })
})

describe('Generation120SecondHintTest', () => {
  it('shows only the 120 second waiting hint and replaces lower tiers', () => {
    const hint = resolveWaitingHint({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:31:00'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hint).toContain('耗时较长')
    expect(hint).not.toContain('无需重复提交')
    expect(hint).not.toContain('复杂应用通常需要一些时间')
  })
})

describe('TerminalStopsWaitingHintsTest', () => {
  it('stops waiting hints when task reaches terminal status', () => {
    expect(resolveWaitingHint({
      taskStatus: 'FAILED',
      events: TASK_90_EVENTS,
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
      activeStageKey: 'TERMINAL_FAILED',
    })).toBeUndefined()
  })
})

describe('TerminalStopsElapsedTimerTest', () => {
  it('marks terminal tasks as completed connection state and removes waiting hint', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'FAILED',
        finishedAt: '2026-07-07T19:33:33',
        errorMessage: 'AI 输出超过长度限制',
      } as GenerationTaskDetailResponse,
      events: TASK_90_EVENTS,
      connectionState: 'LIVE',
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
    })

    expect(viewModel.isTerminal).toBe(true)
    expect(viewModel.connectionState).toBe('COMPLETED')
    expect(viewModel.waitingHint).toBeUndefined()
  })
})

describe('ConnectionStateLiveTest', () => {
  it('maps live stream state to the live label', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: { id: '90', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: [],
      connectionState: 'LIVE',
      nowMs: Date.now(),
    })

    expect(viewModel.connectionLabel).toBe('实时')
  })
})

describe('ConnectionStatePollingFallbackTest', () => {
  it('maps polling fallback state to the polling label', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: { id: '90', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: [],
      connectionState: 'POLLING',
      nowMs: Date.now(),
    })

    expect(viewModel.connectionLabel).toContain('实时连接暂不可用')
  })
})

describe('AttemptChangeDoesNotMislabelCompactRetryTest', () => {
  it('does not expose compact retry wording when attempt changes', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: { id: '90', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: [
        timelineEvent('200', 'MODEL_DELTA', '2026-07-07T19:30:01', {
          attempt: 2,
          receivedChars: 4,
          chunkCount: 1,
          elapsedMs: 1000,
        }),
      ],
      connectionState: 'LIVE',
      nowMs: Date.now(),
      previousAttempt: 1,
    })

    expect(viewModel.retryAttemptMessage).toBe('正在重新尝试生成')
    expect(viewModel.retryAttemptMessage).not.toContain('Compact')
    expect(viewModel.retryAttemptMessage).not.toContain('第 2 次')
  })
})

describe('AttemptChangeShowsGenericRetryTest', () => {
  it('shows a generic retry message when a new attempt starts from a low char count', () => {
    const message = buildGenerationProgressViewModel({
      task: { id: '90', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: [
        timelineEvent('100', 'MODEL_DELTA', '2026-07-07T19:29:10', {
          attempt: 1,
          receivedChars: 14000,
          chunkCount: 3500,
          elapsedMs: 90000,
        }),
        timelineEvent('200', 'MODEL_DELTA', '2026-07-07T19:30:01', {
          attempt: 2,
          receivedChars: 4,
          chunkCount: 1,
          elapsedMs: 1000,
        }),
      ],
      connectionState: 'LIVE',
      nowMs: Date.now(),
      previousAttempt: 1,
    }).retryAttemptMessage

    expect(message).toBeTruthy()
    expect(message).not.toContain('14000')
  })
})

describe('GenerationDuplicateSubmitGuardTest', () => {
  it('detects running generation state for duplicate submit guard', () => {
    expect(isGenerationInProgress('RUNNING', false)).toBe(true)
    expect(isGenerationInProgress('QUEUED', true)).toBe(true)
    expect(isGenerationInProgress('SUCCESS', false)).toBe(false)
  })
})

describe('GenerationRefreshRestoresProgressTest', () => {
  it('restores latest model progress from persisted history', () => {
    const events = [
      timelineEvent('1', 'TASK_CREATED', '2026-07-07T19:28:40'),
      timelineEvent('2', 'TASK_STARTED', '2026-07-07T19:28:41'),
      timelineEvent('3', 'MODEL_CALL_STARTED', '2026-07-07T19:28:50'),
      ...buildModelDeltaEvents(338, 1, 100),
    ]

    const viewModel = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'RUNNING',
        startedAt: '2026-07-07T19:28:40',
      } as GenerationTaskDetailResponse,
      events,
      connectionState: 'SYNCING',
      nowMs: Date.parse('2026-07-07T19:33:00.000Z'),
    })

    expect(viewModel.latestModelProgress?.receivedChars).toBe(338 * 4)
    expect(formatReceivedChars(viewModel.latestModelProgress?.receivedChars ?? 0)).toBe('1,352')
  })
})

describe('GenerationFailedLengthSafeMessageTest', () => {
  it('sanitizes length failure messages for end users', () => {
    expect(resolveSafeFailureMessage('finish_reason=length')).toContain('生成内容过长')
    expect(resolveSafeFailureMessage('AI 输出超过长度限制')).toContain('生成内容过长')
    expect(resolveSafeFailureMessage('finish_reason=length')).not.toContain('finish_reason')
  })
})

describe('GenerationSuccessActionsTest', () => {
  it('marks success terminal state with completed header', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'SUCCESS',
        startedAt: '2026-07-07T19:28:40.000Z',
        finishedAt: '2026-07-07T19:30:00.000Z',
      } as GenerationTaskDetailResponse,
      events: [
        timelineEvent('900', 'TASK_SUCCESS', '2026-07-07T19:30:00.000Z'),
      ],
      connectionState: 'LIVE',
      nowMs: Date.parse('2026-07-07T19:30:00.000Z'),
    })

    expect(viewModel.headerTitle).toBe('生成完成')
    expect(viewModel.terminalEventType).toBe('TASK_SUCCESS')
  })
})

describe('ModelDeltaDoesNotAutoScrollEveryEventTest', () => {
  it('only auto-scrolls when milestone signature changes', () => {
    const before = buildMilestoneSignature([
      { key: 'TASK_CREATED', label: '任务已创建', status: 'completed' },
      { key: 'AI_MODEL', label: 'AI 正在生成项目内容', status: 'active' },
    ])
    const same = before
    const next = buildMilestoneSignature([
      { key: 'TASK_CREATED', label: '任务已创建', status: 'completed' },
      { key: 'AI_MODEL', label: 'AI 正在生成项目内容', status: 'completed' },
      { key: 'FILES_GENERATED', label: '整理生成文件', status: 'active' },
    ])

    expect(shouldAutoScrollTimeline(before, same)).toBe(false)
    expect(shouldAutoScrollTimeline(before, next)).toBe(true)
  })
})

describe('TerminalElapsedUsesFinishedAtTest', () => {
  it('uses task.finishedAt as terminal endAt for failed tasks', () => {
    const task = {
      id: '90',
      taskStatus: 'FAILED',
      finishedAt: '2026-07-07T19:33:33',
    } as GenerationTaskDetailResponse

    const endAtMs = resolveTaskEndAtMs({
      task,
      events: TASK_90_EVENTS,
      taskStatus: 'FAILED',
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
    })

    expect(endAtMs).toBe(Date.parse('2026-07-07T19:33:33'))
  })
})

describe('FailedElapsedUsesTerminalEventFallbackTest', () => {
  it('falls back to TASK_FAILED timestamp when finishedAt is missing', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: { id: '90', taskStatus: 'FAILED' } as GenerationTaskDetailResponse,
      events: TASK_90_EVENTS,
      connectionState: 'COMPLETED',
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
    })

    expect(formatElapsedDuration(viewModel.elapsedMs)).toBe('04:43')
  })
})

describe('SuccessElapsedUsesTerminalEventFallbackTest', () => {
  it('uses finishedAt for success terminal elapsed', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: {
        id: '91',
        taskStatus: 'SUCCESS',
        finishedAt: '2026-07-07T20:00:00',
      } as GenerationTaskDetailResponse,
      events: [
        timelineEvent('1', 'TASK_STARTED', '2026-07-07T19:58:00'),
        timelineEvent('900', 'TASK_SUCCESS', '2026-07-07T20:00:00'),
      ],
      connectionState: 'COMPLETED',
      nowMs: Date.parse('2026-07-07T21:00:00.000Z'),
    })

    expect(formatElapsedDuration(viewModel.elapsedMs)).toBe('02:00')
  })
})

describe('CancelledElapsedUsesTerminalEventFallbackTest', () => {
  it('uses TASK_CANCELLED timestamp when finishedAt is missing', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: { id: '92', taskStatus: 'CANCELLED' } as GenerationTaskDetailResponse,
      events: [
        timelineEvent('1', 'TASK_STARTED', '2026-07-07T19:10:00'),
        timelineEvent('900', 'TASK_CANCELLED', '2026-07-07T19:12:30'),
      ],
      connectionState: 'COMPLETED',
      nowMs: Date.parse('2026-07-07T20:00:00.000Z'),
    })

    expect(formatElapsedDuration(viewModel.elapsedMs)).toBe('02:30')
  })
})

describe('HistoricalTerminalElapsedDoesNotGrowTest', () => {
  it('keeps task 90 elapsed frozen when reopened later', () => {
    const laterNow = Date.parse('2026-07-07T20:12:00.000Z')
    const first = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'FAILED',
        finishedAt: '2026-07-07T19:33:33',
      } as GenerationTaskDetailResponse,
      events: TASK_90_EVENTS,
      connectionState: 'COMPLETED',
      nowMs: laterNow,
    })
    const second = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'FAILED',
        finishedAt: '2026-07-07T19:33:33',
      } as GenerationTaskDetailResponse,
      events: TASK_90_EVENTS,
      connectionState: 'COMPLETED',
      nowMs: laterNow + 30_000,
    })

    expect(formatElapsedDuration(first.elapsedMs)).toBe('04:43')
    expect(second.elapsedMs).toBe(first.elapsedMs)
  })
})

describe('RunningElapsedContinuesGrowingTest', () => {
  it('continues elapsed growth for running tasks', () => {
    const first = buildGenerationProgressViewModel({
      task: { id: '91', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: RUNNING_AI_MODEL_EVENTS,
      connectionState: 'LIVE',
      nowMs: Date.parse('2026-07-07T19:29:20.000Z'),
    })
    const second = buildGenerationProgressViewModel({
      task: { id: '91', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: RUNNING_AI_MODEL_EVENTS,
      connectionState: 'LIVE',
      nowMs: Date.parse('2026-07-07T19:29:50.000Z'),
    })

    expect(second.elapsedMs).toBeGreaterThan(first.elapsedMs)
  })
})

describe('RunningToTerminalFreezesElapsedTest', () => {
  it('freezes elapsed at terminal transition instead of using later wall clock', () => {
    const runningLate = buildGenerationProgressViewModel({
      task: { id: '90', taskStatus: 'RUNNING' } as GenerationTaskDetailResponse,
      events: RUNNING_AI_MODEL_EVENTS,
      connectionState: 'LIVE',
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
    })
    const failed = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'FAILED',
        finishedAt: '2026-07-07T19:33:33',
      } as GenerationTaskDetailResponse,
      events: TASK_90_EVENTS,
      connectionState: 'COMPLETED',
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
    })

    expect(failed.elapsedMs).toBeLessThan(runningLate.elapsedMs)
    expect(formatElapsedDuration(failed.elapsedMs)).toBe('04:43')
  })
})

describe('WaitingHint30OnlyTest', () => {
  it('returns exactly one 30 second hint between 30 and 59 seconds', () => {
    const hints = resolveWaitingHints({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:29:25'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hints).toHaveLength(1)
    expect(hints[0]).toContain('复杂应用通常需要一些时间')
  })
})

describe('WaitingHint60Replaces30Test', () => {
  it('returns exactly one 60 second hint instead of stacking 30 and 60', () => {
    const hints = resolveWaitingHints({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:29:55'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hints).toHaveLength(1)
    expect(hints[0]).toContain('无需重复提交')
  })
})

describe('WaitingHint120Replaces60Test', () => {
  it('returns exactly one 120 second hint instead of stacking all tiers', () => {
    const hints = resolveWaitingHints({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:31:00'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hints).toHaveLength(1)
    expect(hints[0]).toContain('耗时较长')
  })
})

describe('WaitingHintNeverStacksTest', () => {
  it('never returns more than one waiting hint', () => {
    const hint = resolveWaitingHint({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:31:30'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hint).toBeTruthy()
    expect(resolveWaitingHints({
      taskStatus: 'RUNNING',
      events: RUNNING_AI_MODEL_EVENTS,
      nowMs: Date.parse('2026-07-07T19:31:30'),
      activeStageKey: 'AI_MODEL',
    })).toHaveLength(1)
  })
})

describe('TerminalHasNoWaitingHintTest', () => {
  it('returns no waiting hint for terminal tasks', () => {
    const viewModel = buildGenerationProgressViewModel({
      task: {
        id: '90',
        taskStatus: 'FAILED',
        finishedAt: '2026-07-07T19:33:33',
      } as GenerationTaskDetailResponse,
      events: TASK_90_EVENTS,
      connectionState: 'COMPLETED',
      nowMs: Date.parse('2026-07-07T20:12:00.000Z'),
    })

    expect(viewModel.waitingHint).toBeUndefined()
  })
})

describe('NonAiStageHasNoWaitingHintTest', () => {
  it('returns no waiting hint before AI model stage becomes active', () => {
    const hint = resolveWaitingHint({
      taskStatus: 'RUNNING',
      events: [
        timelineEvent('1', 'TASK_CREATED', '2026-07-07T19:28:50'),
        timelineEvent('2', 'TASK_STARTED', '2026-07-07T19:28:50'),
      ],
      nowMs: Date.parse('2026-07-07T19:31:00'),
      activeStageKey: 'TASK_STARTED',
    })

    expect(hint).toBeUndefined()
  })
})

describe('AttemptChangeDoesNotResetModelPhaseWaitingDurationTest', () => {
  it('keeps waiting hint duration based on the first model call start', () => {
    const events = [
      ...RUNNING_AI_MODEL_EVENTS,
      timelineEvent('200', 'MODEL_DELTA', '2026-07-07T19:30:01', {
        attempt: 2,
        receivedChars: 4,
        chunkCount: 1,
        elapsedMs: 1000,
      }),
    ]

    const hint = resolveWaitingHint({
      taskStatus: 'RUNNING',
      events,
      nowMs: Date.parse('2026-07-07T19:31:00'),
      activeStageKey: 'AI_MODEL',
    })

    expect(hint).toContain('耗时较长')
  })
})
