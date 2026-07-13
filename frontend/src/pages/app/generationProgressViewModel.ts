import type { GenerationTaskDetailResponse } from '@/api/task'
import { isTerminalTaskStatus, type TimelineEvent } from '@/pages/app/generationTaskObservation'

export type GenerationConnectionState =
  | 'CONNECTING'
  | 'LIVE'
  | 'SYNCING'
  | 'POLLING'
  | 'COMPLETED'
  | 'DISCONNECTED'

export type MilestoneStatus = 'pending' | 'active' | 'completed'

export interface GenerationMilestoneDefinition {
  key: string
  eventType: string
  label: string
}

export interface GenerationMilestone {
  key: string
  label: string
  status: MilestoneStatus
  createdAt?: string
  eventId?: string
}

export interface ModelProgressSnapshot {
  attempt: number
  receivedChars: number
  chunkCount: number
  elapsedMs: number
  lastUpdatedAt?: string
}

export interface GenerationProgressViewModel {
  taskId?: string
  taskStatus?: string
  connectionState: GenerationConnectionState
  startedAt?: string
  elapsedMs: number
  milestones: GenerationMilestone[]
  activeStageKey?: string
  latestModelProgress: ModelProgressSnapshot | null
  previousAttempt?: number
  attemptChanged: boolean
  waitingHint?: string
  headerTitle: string
  connectionLabel: string
  isTerminal: boolean
  failureMessage?: string
  retryAttemptMessage?: string
  terminalEventType?: string
}

export interface BuildGenerationProgressViewModelInput {
  task?: GenerationTaskDetailResponse | null
  events: TimelineEvent[]
  connectionState: GenerationConnectionState
  connectionLabelOverride?: string
  nowMs: number
  previousAttempt?: number
}

export const MILESTONE_DEFINITIONS: GenerationMilestoneDefinition[] = [
  { key: 'TASK_CREATED', eventType: 'TASK_CREATED', label: '任务已创建' },
  { key: 'TASK_STARTED', eventType: 'TASK_STARTED', label: '开始生成应用' },
  { key: 'PROMPT_RENDERED', eventType: 'PROMPT_RENDERED', label: '生成需求已准备' },
  { key: 'AI_MODEL', eventType: 'MODEL_CALL_STARTED', label: 'AI 正在生成项目内容' },
  { key: 'FILES_GENERATED', eventType: 'FILES_GENERATED', label: '整理生成文件' },
  { key: 'VERSION_CREATED', eventType: 'VERSION_CREATED', label: '创建应用版本' },
]

export const CONNECTION_STATE_LABELS: Record<GenerationConnectionState, string> = {
  CONNECTING: '正在连接实时进度',
  LIVE: '实时',
  SYNCING: '正在恢复任务进度',
  POLLING: '实时连接暂不可用，正在同步状态',
  COMPLETED: '已完成',
  DISCONNECTED: '连接已断开',
}

const WAITING_HINT_30S = 'AI 正在生成项目内容，复杂应用通常需要一些时间。'
const WAITING_HINT_60S = '任务仍在正常处理，无需重复提交，请继续等待。'
const WAITING_HINT_120S = '本次生成耗时较长，系统仍在持续处理，请勿重复提交任务。'

const MILESTONE_EVENT_TYPES = new Set([
  'TASK_CREATED',
  'TASK_STARTED',
  'PROMPT_RENDERED',
  'MODEL_CALL_STARTED',
  'MODEL_CALL_FINISHED',
  'FILES_GENERATED',
  'VERSION_CREATED',
  'TASK_SUCCESS',
  'TASK_FAILED',
  'TASK_CANCELLED',
])

export function isMilestoneTimelineEventType(eventType: string): boolean {
  return MILESTONE_EVENT_TYPES.has(eventType)
}

export function shouldRenderTimelineRow(eventType: string): boolean {
  return eventType !== 'MODEL_DELTA'
}

export function formatElapsedDuration(elapsedMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(elapsedMs / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function formatReceivedChars(receivedChars: number): string {
  return receivedChars.toLocaleString('zh-CN')
}

export function resolveTaskStartedAt(
  task: GenerationTaskDetailResponse | null | undefined,
  events: TimelineEvent[],
): string | undefined {
  if (task?.startedAt) {
    return task.startedAt
  }

  const taskStarted = events.find((event) => event.eventType === 'TASK_STARTED')
  if (taskStarted?.createdAt) {
    return taskStarted.createdAt
  }

  const taskCreated = events.find((event) => event.eventType === 'TASK_CREATED')
  return taskCreated?.createdAt
}

export function resolveElapsedMs(startedAt: string | undefined, endAtMs: number): number {
  if (!startedAt) {
    return 0
  }
  const startedMs = Date.parse(startedAt)
  if (Number.isNaN(startedMs)) {
    return 0
  }
  return Math.max(0, endAtMs - startedMs)
}

const TERMINAL_EVENT_TYPES = ['TASK_SUCCESS', 'TASK_FAILED', 'TASK_CANCELLED'] as const

function resolveTerminalEventType(taskStatus?: string): (typeof TERMINAL_EVENT_TYPES)[number] | undefined {
  if (taskStatus === 'SUCCESS') {
    return 'TASK_SUCCESS'
  }
  if (taskStatus === 'FAILED') {
    return 'TASK_FAILED'
  }
  if (taskStatus === 'CANCELLED') {
    return 'TASK_CANCELLED'
  }
  return undefined
}

function findLatestTerminalEvent(
  events: TimelineEvent[],
  taskStatus?: string,
): TimelineEvent | undefined {
  const preferredType = resolveTerminalEventType(taskStatus)
  const terminalEvents = events.filter((event) => TERMINAL_EVENT_TYPES.includes(event.eventType as (typeof TERMINAL_EVENT_TYPES)[number]))
  if (preferredType) {
    const matched = terminalEvents.filter((event) => event.eventType === preferredType)
    if (matched.length > 0) {
      return matched.sort((left, right) => compareTimelineEventIdSafe(left.id, right.id)).at(-1)
    }
  }
  return terminalEvents.sort((left, right) => compareTimelineEventIdSafe(left.id, right.id)).at(-1)
}

export function resolveTaskEndAtMs(options: {
  task: GenerationTaskDetailResponse | null | undefined
  events: TimelineEvent[]
  taskStatus?: string
  nowMs: number
}): number {
  const { task, events, taskStatus, nowMs } = options

  if (!isTerminalTaskStatus(taskStatus)) {
    return nowMs
  }

  if (task?.finishedAt) {
    const finishedMs = Date.parse(task.finishedAt)
    if (!Number.isNaN(finishedMs)) {
      return finishedMs
    }
  }

  const terminalEvent = findLatestTerminalEvent(events, taskStatus)
  if (terminalEvent?.createdAt) {
    const terminalMs = Date.parse(terminalEvent.createdAt)
    if (!Number.isNaN(terminalMs)) {
      return terminalMs
    }
  }

  return nowMs
}

function readNumericField(data: Record<string, unknown> | undefined, field: string): number | undefined {
  const value = data?.[field]
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && /^\d+$/.test(value)) {
    return Number(value)
  }
  return undefined
}

export function resolveLatestModelProgress(events: TimelineEvent[]): ModelProgressSnapshot | null {
  const modelDeltaEvents = events.filter((event) => event.eventType === 'MODEL_DELTA')
  if (modelDeltaEvents.length === 0) {
    return null
  }

  const latestAttempt = Math.max(
    ...modelDeltaEvents.map((event) => readNumericField(event.data, 'attempt') ?? 1),
  )

  const latestForAttempt = modelDeltaEvents
    .filter((event) => (readNumericField(event.data, 'attempt') ?? 1) === latestAttempt)
    .sort((left, right) => compareTimelineEventIdSafe(left.id, right.id))
    .at(-1)

  if (!latestForAttempt) {
    return null
  }

  return {
    attempt: latestAttempt,
    receivedChars: readNumericField(latestForAttempt.data, 'receivedChars') ?? 0,
    chunkCount: readNumericField(latestForAttempt.data, 'chunkCount') ?? 0,
    elapsedMs: readNumericField(latestForAttempt.data, 'elapsedMs') ?? 0,
    lastUpdatedAt: latestForAttempt.createdAt,
  }
}

function compareTimelineEventIdSafe(leftId?: string, rightId?: string): number {
  try {
    const left = BigInt(leftId || '0')
    const right = BigInt(rightId || '0')
    if (left === right) {
      return 0
    }
    return left > right ? 1 : -1
  } catch {
    return 0
  }
}

function findLatestEvent(events: TimelineEvent[], eventType: string): TimelineEvent | undefined {
  return [...events]
    .filter((event) => event.eventType === eventType)
    .sort((left, right) => compareTimelineEventIdSafe(left.id, right.id))
    .at(-1)
}

function hasEvent(events: TimelineEvent[], eventType: string): boolean {
  return events.some((event) => event.eventType === eventType)
}

export function resolveModelCallStartedAt(events: TimelineEvent[]): string | undefined {
  const modelCallStartedEvents = events.filter((event) => event.eventType === 'MODEL_CALL_STARTED')
  if (modelCallStartedEvents.length === 0) {
    return undefined
  }
  return modelCallStartedEvents
    .sort((left, right) => compareTimelineEventIdSafe(left.id, right.id))[0]
    ?.createdAt
}

export function resolveWaitingHint(options: {
  taskStatus?: string
  events: TimelineEvent[]
  nowMs: number
  activeStageKey?: string
}): string | undefined {
  const { taskStatus, events, nowMs, activeStageKey } = options

  if (isTerminalTaskStatus(taskStatus)) {
    return undefined
  }

  if (taskStatus !== 'RUNNING' && taskStatus !== 'QUEUED') {
    return undefined
  }

  if (activeStageKey !== 'AI_MODEL') {
    return undefined
  }

  if (hasEvent(events, 'MODEL_CALL_FINISHED')) {
    return undefined
  }

  const modelCallStartedAt = resolveModelCallStartedAt(events)
  if (!modelCallStartedAt) {
    return undefined
  }

  const modelCallStartedMs = Date.parse(modelCallStartedAt)
  if (Number.isNaN(modelCallStartedMs)) {
    return undefined
  }

  const modelElapsedMs = Math.max(0, nowMs - modelCallStartedMs)

  if (modelElapsedMs >= 120_000) {
    return WAITING_HINT_120S
  }
  if (modelElapsedMs >= 60_000) {
    return WAITING_HINT_60S
  }
  if (modelElapsedMs >= 30_000) {
    return WAITING_HINT_30S
  }

  return undefined
}

/** @deprecated Use resolveWaitingHint – kept for transitional imports */
export function resolveWaitingHints(options: {
  taskStatus?: string
  events: TimelineEvent[]
  nowMs: number
  latestModelProgress?: ModelProgressSnapshot | null
  activeStageKey?: string
}): string[] {
  const hint = resolveWaitingHint({
    taskStatus: options.taskStatus,
    events: options.events,
    nowMs: options.nowMs,
    activeStageKey: options.activeStageKey,
  })
  return hint ? [hint] : []
}

export function resolveSafeFailureMessage(errorMessage?: string, errorCode?: string): string {
  const normalized = (errorMessage || '').trim()
  const code = (errorCode || '').trim()

  if (/finish_reason\s*=\s*length/i.test(normalized) || /finish_reason/i.test(normalized)) {
    return '生成内容过长，AI 在多次尝试后仍未能生成完整结果。建议适当减少页面模块或缩小单次需求范围后重试。'
  }

  if (/长度|过长|too long|length limit|max tokens|token limit/i.test(normalized) || /LENGTH/i.test(code)) {
    return '生成内容过长，AI 在多次尝试后仍未能生成完整结果。建议适当减少页面模块或缩小单次需求范围后重试。'
  }

  if (!normalized) {
    return '生成失败'
  }

  return normalized
}

function resolveHeaderTitle(taskStatus?: string): string {
  switch (taskStatus) {
    case 'RUNNING':
    case 'QUEUED':
      return 'AI 正在生成应用'
    case 'SUCCESS':
      return '生成完成'
    case 'FAILED':
      return '生成失败'
    case 'CANCELLED':
      return '任务已取消'
    default:
      return 'AI 生成'
  }
}

function resolveActiveStageKey(events: TimelineEvent[], taskStatus?: string): string | undefined {
  if (isTerminalTaskStatus(taskStatus)) {
    if (taskStatus === 'FAILED') {
      return 'TERMINAL_FAILED'
    }
    if (taskStatus === 'CANCELLED') {
      return 'TERMINAL_CANCELLED'
    }
    return 'TERMINAL_SUCCESS'
  }

  if (hasEvent(events, 'VERSION_CREATED') && !hasEvent(events, 'TASK_SUCCESS')) {
    return 'VERSION_CREATED'
  }
  if (hasEvent(events, 'FILES_GENERATED') && !hasEvent(events, 'VERSION_CREATED')) {
    return 'FILES_GENERATED'
  }
  if (hasEvent(events, 'MODEL_CALL_FINISHED') && !hasEvent(events, 'FILES_GENERATED')) {
    return 'POST_MODEL'
  }
  if (hasEvent(events, 'MODEL_CALL_STARTED') && !hasEvent(events, 'MODEL_CALL_FINISHED')) {
    return 'AI_MODEL'
  }
  if (hasEvent(events, 'PROMPT_RENDERED') && !hasEvent(events, 'MODEL_CALL_STARTED')) {
    return 'PROMPT_RENDERED'
  }
  if (hasEvent(events, 'TASK_STARTED') && !hasEvent(events, 'PROMPT_RENDERED')) {
    return 'TASK_STARTED'
  }
  if (hasEvent(events, 'TASK_CREATED')) {
    return 'TASK_CREATED'
  }
  return undefined
}

export function buildGenerationMilestones(events: TimelineEvent[], taskStatus?: string): GenerationMilestone[] {
  const activeStageKey = resolveActiveStageKey(events, taskStatus)
  const stageOrder = MILESTONE_DEFINITIONS.map((item) => item.key)
  const activeIndex = activeStageKey ? stageOrder.indexOf(activeStageKey) : -1

  return MILESTONE_DEFINITIONS.map((definition, index) => {
    const matchedEvent = findLatestEvent(events, definition.eventType)
    let status: MilestoneStatus = 'pending'

    if (matchedEvent) {
      status = 'completed'
    } else if (definition.key === 'AI_MODEL' && hasEvent(events, 'MODEL_CALL_STARTED')) {
      status = hasEvent(events, 'MODEL_CALL_FINISHED') ? 'completed' : 'active'
    } else if (activeIndex >= 0) {
      if (index < activeIndex) {
        status = 'completed'
      } else if (index === activeIndex) {
        status = 'active'
      }
    }

    if (
      definition.key === 'AI_MODEL'
      && hasEvent(events, 'MODEL_CALL_STARTED')
      && !hasEvent(events, 'MODEL_CALL_FINISHED')
      && !isTerminalTaskStatus(taskStatus)
    ) {
      status = 'active'
    }

    if (matchedEvent && definition.key !== 'AI_MODEL') {
      status = 'completed'
    }

    return {
      key: definition.key,
      label: definition.label,
      status,
      createdAt: matchedEvent?.createdAt,
      eventId: matchedEvent?.id,
    }
  })
}

export function resolveRetryAttemptMessage(options: {
  latestModelProgress: ModelProgressSnapshot | null
  previousAttempt?: number
  events: TimelineEvent[]
}): string | undefined {
  const { latestModelProgress, previousAttempt, events } = options
  if (!latestModelProgress) {
    return undefined
  }

  const attempts = new Set<number>()
  for (const event of events) {
    if (event.eventType === 'MODEL_DELTA') {
      attempts.add(readNumericField(event.data, 'attempt') ?? 1)
    }
  }

  if (attempts.size <= 1 && !(previousAttempt && previousAttempt !== latestModelProgress.attempt)) {
    return undefined
  }

  if (previousAttempt && previousAttempt !== latestModelProgress.attempt) {
    return '正在重新尝试生成'
  }

  if (latestModelProgress.attempt > 1 && latestModelProgress.receivedChars <= 32) {
    return '已开始新的生成尝试'
  }

  return undefined
}

export function buildGenerationProgressViewModel(options: {
  task: GenerationTaskDetailResponse | null | undefined
  events: TimelineEvent[]
  connectionState: GenerationConnectionState
  connectionLabelOverride?: string
  nowMs: number
  previousAttempt?: number
}): GenerationProgressViewModel {
  const { task, events, connectionState, connectionLabelOverride, nowMs, previousAttempt } = options
  const taskStatus = task?.taskStatus
  const startedAt = resolveTaskStartedAt(task, events)
  const endAtMs = resolveTaskEndAtMs({ task, events, taskStatus, nowMs })
  const elapsedMs = resolveElapsedMs(startedAt, endAtMs)
  const latestModelProgress = resolveLatestModelProgress(events)
  const milestones = buildGenerationMilestones(events, taskStatus)
  const activeStageKey = resolveActiveStageKey(events, taskStatus)
  const attemptChanged = Boolean(
    previousAttempt && latestModelProgress && previousAttempt !== latestModelProgress.attempt,
  )
  const waitingHint = resolveWaitingHint({
    taskStatus,
    events,
    nowMs,
    activeStageKey,
  })
  const isTerminal = isTerminalTaskStatus(taskStatus)
  const terminalEvent = findLatestTerminalEvent(events, taskStatus)

  return {
    taskId: task?.id ? String(task.id) : undefined,
    taskStatus,
    connectionState: isTerminal ? 'COMPLETED' : connectionState,
    startedAt,
    elapsedMs,
    milestones,
    activeStageKey,
    latestModelProgress,
    previousAttempt,
    attemptChanged,
    waitingHint,
    headerTitle: resolveHeaderTitle(taskStatus),
    connectionLabel: isTerminal
      ? CONNECTION_STATE_LABELS.COMPLETED
      : connectionLabelOverride || CONNECTION_STATE_LABELS[connectionState],
    isTerminal,
    failureMessage:
      taskStatus === 'FAILED'
        ? resolveSafeFailureMessage(task?.errorMessage, task?.errorCode)
        : undefined,
    retryAttemptMessage: resolveRetryAttemptMessage({
      latestModelProgress,
      previousAttempt,
      events,
    }),
    terminalEventType: terminalEvent?.eventType,
  }
}

export function shouldAutoScrollTimeline(previousMilestoneSignature: string, nextMilestoneSignature: string): boolean {
  return previousMilestoneSignature !== nextMilestoneSignature
}

export function buildMilestoneSignature(milestones: GenerationMilestone[]): string {
  return milestones
    .map((milestone) => `${milestone.key}:${milestone.status}:${milestone.eventId || ''}`)
    .join('|')
}

export function isGenerationInProgress(taskStatus?: string, generating?: boolean): boolean {
  return Boolean(generating || taskStatus === 'RUNNING' || taskStatus === 'QUEUED')
}
