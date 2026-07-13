<template>
  <div class="generate-page">
    <div class="page-toolbar">
      <a-space>
        <a-button size="small" @click="router.push(`/apps/${appId}`)">返回详情</a-button>
        <h2 class="page-heading">AI 生成</h2>
        <a-tag v-if="appInfo?.appType" color="blue">{{ appInfo.appType }}</a-tag>
      </a-space>
    </div>

    <a-row :gutter="16" class="generate-layout">
      <a-col :span="6">
        <a-card title="需求输入" size="small" class="panel-card">
          <a-form :model="form" layout="vertical">
            <a-form-item label="应用名称">
              <a-input :value="appInfo?.name || '-'" disabled />
            </a-form-item>
            <a-form-item label="工作空间">
              <a-input :value="workspaceDisplay" disabled />
            </a-form-item>
            <a-form-item label="需求描述">
              <a-textarea
                v-model:value="form.requirement"
                :rows="8"
                placeholder="描述你希望生成的页面、模块和关键字段"
              />
            </a-form-item>
            <a-form-item label="提示词模板">
              <div class="template-selector-block">
                <a-button block @click="templateDrawerOpen = true">选择模板</a-button>
                <div v-if="selectedTemplate" class="selected-template">
                  <div>已选择：{{ selectedTemplate.templateName }}</div>
                  <div>模板版本：v{{ selectedTemplate.versionNo }}</div>
                  <a-space>
                    <a-button type="link" size="small" @click="templateDrawerOpen = true">更换</a-button>
                    <a-button type="link" size="small" danger @click="clearSelectedTemplate">清除</a-button>
                  </a-space>
                </div>
              </div>
            </a-form-item>
            <template v-if="selectedTemplate?.variables.length">
              <a-form-item
                v-for="variable in selectedTemplate.variables"
                :key="variable.key"
                :label="variableFieldLabel(variable.key)"
                :required="variable.required"
              >
                <a-input
                  v-model:value="selectedTemplate.variableValues[variable.key]"
                  :placeholder="variable.description || `请输入 ${variable.key}`"
                />
              </a-form-item>
            </template>
            <a-button
              type="primary"
              block
              :loading="generating && !isTaskRunning"
              :disabled="!appInfo || isTaskRunning"
              @click="startGeneration"
            >
              <ThunderboltOutlined />
              开始生成
            </a-button>
            <div v-if="isTaskRunning" class="running-hint">当前任务正在生成中</div>
          </a-form>
        </a-card>
      </a-col>

      <a-col :span="10">
        <a-card title="生成过程" size="small" class="panel-card">
          <a-spin :spinning="pageLoading || (generating && events.length === 0)">
            <div v-if="currentTaskId" class="generation-header">
              <div class="generation-header-main">
                <div class="generation-header-title">{{ progressViewModel.headerTitle }}</div>
                <div class="generation-header-meta">
                  <span class="task-id">任务 #{{ currentTaskId }}</span>
                  <span v-if="progressViewModel.elapsedMs > 0" class="elapsed-label">
                    已用时 {{ formatElapsedDuration(progressViewModel.elapsedMs) }}
                  </span>
                </div>
              </div>
              <div class="generation-header-status">
                <span
                  class="connection-dot"
                  :class="{
                    live: connectionState === 'LIVE',
                    polling: connectionState === 'POLLING',
                    syncing: connectionState === 'SYNCING',
                  }"
                ></span>
                <span class="connection-label">{{ progressViewModel.connectionLabel }}</span>
              </div>
            </div>

            <div v-if="progressViewModel.milestones.length" ref="timelineContainerRef" class="milestone-timeline">
              <div
                v-for="milestone in progressViewModel.milestones"
                :key="milestone.key"
                class="milestone-item"
                :class="`milestone-${milestone.status}`"
              >
                <div class="milestone-marker">
                  <span v-if="milestone.status === 'completed'">✓</span>
                  <span v-else-if="milestone.status === 'active'" class="milestone-active-dot"></span>
                  <span v-else>○</span>
                </div>
                <div class="milestone-content">
                  <div class="milestone-label">{{ milestone.label }}</div>
                  <div
                    v-if="milestone.key === 'AI_MODEL' && milestone.status === 'active'"
                    class="model-progress-block"
                  >
                    <div v-if="progressViewModel.retryAttemptMessage" class="retry-attempt-message">
                      {{ progressViewModel.retryAttemptMessage }}
                    </div>
                    <div v-if="progressViewModel.latestModelProgress" class="model-progress-detail">
                      已接收 {{ formatReceivedChars(progressViewModel.latestModelProgress.receivedChars) }} 字符
                    </div>
                    <div v-else-if="hasModelCallStarted" class="model-progress-detail">正在等待模型输出</div>
                  </div>
                  <div v-if="milestone.createdAt" class="milestone-time">{{ formatTime(milestone.createdAt) }}</div>
                </div>
              </div>

              <div v-if="progressViewModel.isTerminal && progressViewModel.terminalEventType === 'TASK_FAILED'" class="milestone-item milestone-completed failed">
                <div class="milestone-marker">✕</div>
                <div class="milestone-content">
                  <div class="milestone-label">生成失败</div>
                </div>
              </div>
              <div v-if="progressViewModel.isTerminal && progressViewModel.terminalEventType === 'TASK_SUCCESS'" class="milestone-item milestone-completed">
                <div class="milestone-marker">✓</div>
                <div class="milestone-content">
                  <div class="milestone-label">生成完成</div>
                </div>
              </div>
              <div v-if="progressViewModel.isTerminal && progressViewModel.terminalEventType === 'TASK_CANCELLED'" class="milestone-item milestone-completed">
                <div class="milestone-marker">○</div>
                <div class="milestone-content">
                  <div class="milestone-label">任务已取消</div>
                </div>
              </div>
            </div>

            <div v-if="progressViewModel.waitingHint" class="waiting-hints">
              <div class="waiting-hint">
                {{ progressViewModel.waitingHint }}
              </div>
            </div>

            <a-empty
              v-if="!pageLoading && !generating && !progressViewModel.milestones.length"
              description="输入需求后，点击“开始生成”创建任务"
            />
          </a-spin>
        </a-card>
      </a-col>

      <a-col :span="8">
        <a-card title="生成结果" size="small" class="panel-card">
          <div v-if="isTaskRunning" class="result-running">
            <a-spin size="small" />
            <span>AI 正在生成中</span>
          </div>
          <div v-else-if="previewUrl" class="preview-container">
            <iframe :src="previewUrl" title="应用预览" class="preview-frame"></iframe>
          </div>
          <div v-else-if="currentTask?.taskStatus === 'SUCCESS' && filesLoadError" class="error-block">
            <a-alert
              type="warning"
              message="生成已完成，但文件列表加载失败"
              show-icon
            />
            <a-button type="link" size="small" @click="retryLoadFiles">重试加载</a-button>
          </div>
          <div v-else-if="currentTask?.taskStatus === 'SUCCESS' && files.length">
            <div class="success-summary">共 {{ files.length }} 个文件</div>
            <div v-for="file in files" :key="file.id" class="file-item">
              <FileOutlined class="file-icon" />
              <span>{{ file.filePath || file.fileName }}</span>
            </div>
            <a-space class="success-actions" wrap>
              <a-button v-if="previewUrl" type="primary" size="small" @click="scrollToPreview">打开预览</a-button>
              <a-button size="small" @click="browseGeneratedFiles">浏览文件</a-button>
            </a-space>
          </div>
          <div v-else-if="currentTask?.taskStatus === 'FAILED'" class="error-block">
            <a-alert
              type="error"
              :message="progressViewModel.failureMessage || '生成失败'"
              show-icon
            />
            <a-space class="failed-actions">
              <a-button type="primary" size="small" @click="startGeneration">重新生成</a-button>
              <a-button size="small" @click="focusRequirement">调整需求</a-button>
            </a-space>
          </div>
          <a-empty v-else description="生成成功后将在这里显示预览或文件列表" />
        </a-card>
      </a-col>
    </a-row>

    <TemplateSelectorDrawer
      :open="templateDrawerOpen"
      @close="templateDrawerOpen = false"
      @preview="openTemplatePreview"
      @select="applySelectedTemplate"
    />
    <PromptTemplateDetailDrawer
      :open="previewDrawerOpen"
      :loading="previewLoading"
      :detail="previewDetail"
      @close="previewDrawerOpen = false"
      @use="applyTemplateFromPreview"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { FileOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { getAiApp, type AiAppDetailResponse } from '@/api/app'
import { listAppVersionFiles, type AppVersionFileResponse } from '@/api/appVersion'
import { createAppVersionPreviewToken } from '@/api/preview'
import { getPublishedTemplate } from '@/api/promptTemplate'
import {
  getGenerationTask,
  listGenerationTaskEvents,
  type GenerationTaskDetailResponse,
  type PublicGenerationStreamEvent,
} from '@/api/task'
import PromptTemplateDetailDrawer from '@/components/prompt/PromptTemplateDetailDrawer.vue'
import TemplateSelectorDrawer from '@/components/prompt/TemplateSelectorDrawer.vue'
import { getAccessToken } from '@/auth/token'
import { API_BASE_URL, APP_BASE_URL } from '@/config/env'
import { consumeSseStream, SseStreamError, classifySseStreamError } from '@/utils/sseStream'
import {
  buildGenerationProgressViewModel,
  buildMilestoneSignature,
  formatElapsedDuration,
  formatReceivedChars,
  isGenerationInProgress,
  resolveLatestModelProgress,
  shouldAutoScrollTimeline,
  type GenerationConnectionState,
} from '@/pages/app/generationProgressViewModel'
import { resolveGenerationResultVersionId } from '@/pages/app/generationVersionResolution'
import { normalizeLongId } from '@/utils/normalizeLongId'
import {
  GenerationStreamRecoveryController,
} from '@/pages/app/generationStreamRecoveryController'
import {
  awaitCreationPhase,
  classifyStartGenerationCatchErrorSource,
  detachObservation,
  isTaskIdentityEstablished,
  shouldRunCreationErrorCleanup,
} from '@/pages/app/generationLifecycle'

import type { IdLike, LongId } from '@/types/id'
import { resolveQueryTaskId } from '@/pages/app/generationDetailNavigation'
import {
  buildGeneratedFilesBrowseTarget,
  MISSING_GENERATION_VERSION_MESSAGE,
  resolveBrowseVersionId,
} from '@/pages/app/generationBrowseNavigation'
import {
  isTerminalTaskStatus,
  mergeTimelineEvent,
  normalizeTaskId,
  resolveLastStreamEventId,
  shouldSkipDuplicateObservation,
  timelineEventsFromPublicEvents,
  type TimelineEvent,
} from '@/pages/app/generationTaskObservation'
import {
  buildGenerationTaskTemplatePayload,
  resolveRouteTemplateId,
  resolveRouteTemplateVersionId,
} from '@/utils/promptTemplateWorkspace'
import { validateTemplateVariableValues, variableFieldLabel } from '@/utils/promptTemplateVariables'
import type { PromptTemplateUserDetail, SelectedPromptTemplate } from '@/utils/promptTemplateTypes'

interface TaskEventPayloadDetail {
  taskStatus?: string
  versionId?: LongId
  fileCount?: number
  error?: string
  errorCode?: string
  [key: string]: string | number | undefined
}

interface TaskCreationEntry {
  taskId: LongId
  workspaceId: LongId
  appId: LongId
  taskType: string
  taskStatus: string
  queuedAt?: string
}

const route = useRoute()
const router = useRouter()

const appId = String(route.params.appId ?? '')
const pageLoading = ref(false)
const generating = ref(false)
const appInfo = ref<AiAppDetailResponse | null>(null)
const currentTask = ref<GenerationTaskDetailResponse | null>(null)
const currentTaskId = ref<LongId>()
const events = ref<TimelineEvent[]>([])
const files = ref<AppVersionFileResponse[]>([])
const filesLoadError = ref(false)
const resolvedVersionId = ref<LongId | null>(null)
const previewUrl = ref('')
const templateDrawerOpen = ref(false)
const previewDrawerOpen = ref(false)
const previewLoading = ref(false)
const previewDetail = ref<PromptTemplateUserDetail | null>(null)
const selectedTemplate = ref<SelectedPromptTemplate | null>(null)
const connectionState = ref<GenerationConnectionState>('CONNECTING')
const connectionLabelOverride = ref<string | undefined>()
const nowMs = ref(Date.now())
const previousModelAttempt = ref<number | undefined>()
const timelineContainerRef = ref<HTMLElement | null>(null)
const lastMilestoneSignature = ref('')

let taskFinalized = false
let lastStreamEventId: string | undefined
let observedTaskId: string | undefined

const resetTaskFinalization = () => {
  taskFinalized = false
}

const recoveryController = new GenerationStreamRecoveryController(
  {
    onConnectionStateChange: (state, labelOverride) => {
      connectionState.value = state
      connectionLabelOverride.value = labelOverride
    },
    onEventsMerged: (incoming) => {
      for (const event of incoming) {
        mergeEvent(event)
        applyTimelineEventSideEffects(event)
      }
    },
    onTerminal: async () => {
      const versionIdText = extractDataField(events.value.at(-1)?.data, 'versionId')
      await handleTaskFinished(versionIdText)
    },
    onNonRetryableFailure: (error) => {
      const classification = classifySseStreamError(error)
      if (classification.kind === 'auth') {
        message.error('未登录或登录已过期')
        return
      }
      if (classification.status === 403) {
        message.error('无权访问该生成任务')
        return
      }
      if (classification.status === 404) {
        message.error('生成任务不存在')
      }
    },
  },
  {
    connectStream: async (options) => {
      const accessToken = getAccessToken()
      if (!accessToken) {
        throw new SseStreamError('未登录或登录已过期', 401, false)
      }

      await consumeSseStream<PublicGenerationStreamEvent>({
        url: `${API_BASE_URL}/generation-tasks/${options.taskId}/stream`,
        token: accessToken,
        signal: options.signal,
        lastEventId: options.lastEventId,
        onConnected: options.onConnected,
        onActivity: options.onActivity,
        onEvent: (streamEvent) => {
          options.onEvent({
            id: streamEvent.id,
            type: streamEvent.type,
            data: streamEvent.data as unknown as Record<string, unknown>,
          })
        },
        onTerminal: (streamEvent) => {
          options.onTerminal({
            id: streamEvent.id,
            type: streamEvent.type,
            data: streamEvent.data as unknown as Record<string, unknown>,
          })
        },
      })
    },
    pollTask: async (taskId, afterEventId) => {
      await loadTaskDetail(taskId)
      const eventRes = await listGenerationTaskEvents(taskId, afterEventId)
      const incomingEvents =
        eventRes.data.code === 0 ? timelineEventsFromPublicEvents(eventRes.data.data || []) : []
      return {
        events: incomingEvents,
        taskStatus: currentTask.value?.taskStatus,
        terminal: isTerminalTaskStatus(currentTask.value?.taskStatus),
      }
    },
  },
)

const form = reactive({
  requirement: '',
})

const workspaceDisplay = computed(() => {
  if (!appInfo.value?.workspaceId) {
    return '-'
  }
  return `#${appInfo.value.workspaceId}`
})

const progressViewModel = computed(() =>
  buildGenerationProgressViewModel({
    task: currentTask.value,
    events: events.value,
    connectionState: connectionState.value,
    connectionLabelOverride: connectionLabelOverride.value,
    nowMs: nowMs.value,
    previousAttempt: previousModelAttempt.value,
  }),
)

const isTaskRunning = computed(() =>
  isGenerationInProgress(currentTask.value?.taskStatus, generating.value),
)

const hasModelCallStarted = computed(() =>
  events.value.some((event) => event.eventType === 'MODEL_CALL_STARTED'),
)

const stopElapsedTimer = () => {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

const startElapsedTimer = () => {
  stopElapsedTimer()
  if (isTerminalTaskStatus(currentTask.value?.taskStatus)) {
    return
  }
  elapsedTimer = setInterval(() => {
    if (isTerminalTaskStatus(currentTask.value?.taskStatus)) {
      stopElapsedTimer()
      return
    }
    nowMs.value = Date.now()
  }, 1000)
}

const scrollTimelineToBottom = async () => {
  await nextTick()
  const container = timelineContainerRef.value
  if (!container) {
    return
  }
  container.scrollTop = container.scrollHeight
}

const focusRequirement = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const scrollToPreview = () => {
  const preview = document.querySelector('.preview-container')
  preview?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

const formatTime = (value?: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

let elapsedTimer: ReturnType<typeof setInterval> | null = null

const stopObservation = () => {
  recoveryController.stop()
}

const handleObservationFailure = (error: unknown) => {
  console.error('[generation] observation failure', error)
}

const startGenerationTaskObservation = (taskIdStr: string) => {
  detachObservation(
    () => recoveryController.start(taskIdStr, lastStreamEventId),
    handleObservationFailure,
  )
}

const toPreviewIframeUrl = (rawPreviewUrl: string) => {
  if (/^https?:\/\//i.test(rawPreviewUrl)) {
    return rawPreviewUrl
  }
  return new URL(rawPreviewUrl, `${APP_BASE_URL}/`).toString()
}

const ensurePreviewUrl = async (versionId?: IdLike) => {
  if (!versionId) {
    return
  }
  if (!appInfo.value?.id) {
    return
  }
  try {
    const res = await createAppVersionPreviewToken(appInfo.value.id, versionId)
    if (res.data.code === 0 && res.data.data?.previewUrl) {
      previewUrl.value = toPreviewIframeUrl(res.data.data.previewUrl)
    }
  } catch {
    previewUrl.value = ''
  }
}

const loadApp = async () => {
  if (!appId) {
    message.error('应用 ID 不存在')
    await router.push('/apps')
    return
  }

  pageLoading.value = true
  try {
    const res = await getAiApp(appId)
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      return
    }
    message.error(res.data.message || '加载应用失败')
    await router.push('/apps')
  } catch (error: any) {
    message.error(error?.message || '加载应用失败')
    await router.push('/apps')
  } finally {
    pageLoading.value = false
  }
}

const loadTemplatesFromRoute = async () => {
  const templateIdText = resolveRouteTemplateId(route.query)
  const templateVersionIdText = resolveRouteTemplateVersionId(route.query)
  if (!templateIdText) return
  const templateId = Number(templateIdText)
  if (!Number.isFinite(templateId)) return
  try {
    const res = await getPublishedTemplate(templateId)
    if (res.data.code !== 0 || !res.data.data) return
    const detail = res.data.data
    const versionId = templateVersionIdText ? Number(templateVersionIdText) : detail.publishedVersion.id
    if (!Number.isFinite(versionId)) return
    selectedTemplate.value = {
      templateId: detail.id,
      templateVersionId: versionId,
      templateName: detail.templateName,
      versionNo: detail.publishedVersion.versionNo,
      applicableAppType: detail.applicableAppType,
      variables: detail.variables,
      variableValues: {},
    }
  } catch {
    // keep page usable
  }
}

const applySelectedTemplate = (selection: SelectedPromptTemplate) => {
  selectedTemplate.value = {
    ...selection,
    variableValues: { ...selection.variableValues },
  }
}

const applyTemplateFromPreview = (detail: PromptTemplateUserDetail) => {
  selectedTemplate.value = {
    templateId: detail.id,
    templateVersionId: detail.publishedVersion.id,
    templateName: detail.templateName,
    versionNo: detail.publishedVersion.versionNo,
    applicableAppType: detail.applicableAppType,
    variables: detail.variables,
    variableValues: {},
  }
  previewDrawerOpen.value = false
}

const clearSelectedTemplate = () => {
  selectedTemplate.value = null
}

const openTemplatePreview = async (templateId: number) => {
  previewDrawerOpen.value = true
  previewLoading.value = true
  previewDetail.value = null
  try {
    const res = await getPublishedTemplate(templateId)
    if (res.data.code !== 0 || !res.data.data) {
      message.error(res.data.message || '加载模板详情失败')
      return
    }
    previewDetail.value = res.data.data
  } catch (error: any) {
    message.error(error?.message || '加载模板详情失败')
  } finally {
    previewLoading.value = false
  }
}

const resolveResultVersionId = () =>
  resolveGenerationResultVersionId({
    currentVersionId: appInfo.value?.currentVersionId,
    events: events.value,
  })

const loadFiles = async (versionId?: IdLike) => {
  const normalizedVersionId = normalizeLongId(versionId) ?? resolveResultVersionId()
  if (!appInfo.value?.id || !normalizedVersionId) {
    files.value = []
    filesLoadError.value = false
    resolvedVersionId.value = null
    return
  }
  resolvedVersionId.value = normalizedVersionId
  filesLoadError.value = false
  try {
    const res = await listAppVersionFiles(appId, normalizedVersionId)
    if (res.data.code === 0) {
      files.value = res.data.data || []
      await ensurePreviewUrl(normalizedVersionId)
    } else {
      files.value = []
      filesLoadError.value = true
    }
  } catch {
    files.value = []
    filesLoadError.value = true
  }
}

const retryLoadFiles = async () => {
  if (resolvedVersionId.value) {
    await loadFiles(resolvedVersionId.value)
    return
  }
  await loadFiles(resolveResultVersionId() ?? undefined)
}

const browseGeneratedFiles = async () => {
  const versionId = resolveBrowseVersionId({
    resolvedVersionId: resolvedVersionId.value,
    currentVersionId: appInfo.value?.currentVersionId,
    events: events.value,
  })
  const target = buildGeneratedFilesBrowseTarget(appId, versionId)
  if (!target) {
    message.warning(MISSING_GENERATION_VERSION_MESSAGE)
    return
  }
  await router.push(target)
}

const loadTaskDetail = async (taskId: string) => {
  const taskRes = await getGenerationTask(taskId)
  if (taskRes.data.code === 0 && taskRes.data.data) {
    currentTask.value = taskRes.data.data
  }
}

const loadAndMergeTaskEvents = async (taskId: string, afterEventId?: string) => {
  connectionState.value = 'SYNCING'
  connectionLabelOverride.value = undefined
  const eventRes = await listGenerationTaskEvents(taskId, afterEventId)
  if (eventRes.data.code !== 0) {
    if (!isTerminalTaskStatus(currentTask.value?.taskStatus)) {
      connectionState.value = recoveryController.hasPollingTimer() ? 'POLLING' : 'DISCONNECTED'
    }
    return
  }

  const incoming = timelineEventsFromPublicEvents(eventRes.data.data || [])
  for (const event of incoming) {
    mergeEvent(event)
  }

  if (!isTerminalTaskStatus(currentTask.value?.taskStatus)) {
    if (recoveryController.hasActiveStream()) {
      connectionState.value = 'LIVE'
    } else if (recoveryController.hasPollingTimer()) {
      connectionState.value = 'POLLING'
    } else if (recoveryController.hasReconnectTimer()) {
      connectionState.value = 'DISCONNECTED'
      connectionLabelOverride.value = '正在恢复实时连接'
    } else {
      connectionState.value = 'DISCONNECTED'
    }
  }
}

const syncTerminalTaskState = async (taskId: string) => {
  const [taskRes, eventRes] = await Promise.all([getGenerationTask(taskId), listGenerationTaskEvents(taskId)])

  if (taskRes.data.code === 0 && taskRes.data.data) {
    currentTask.value = taskRes.data.data
  }
  if (eventRes.data.code === 0) {
    events.value = timelineEventsFromPublicEvents(eventRes.data.data || [])
    lastStreamEventId = resolveLastStreamEventId(events.value)
  }
}

const loadTaskFromQuery = async () => {
  const taskId = resolveQueryTaskId(route.query)
  if (!taskId) {
    return
  }

  await observeGenerationTask(taskId, { skipRouteUpdate: true })
}

const bindGenerationTask = async (
  taskIdStr: string,
  options: { skipRouteUpdate?: boolean } = {},
): Promise<{ terminal: boolean }> => {
  stopObservation()

  resetTaskFinalization()
  observedTaskId = taskIdStr
  currentTaskId.value = taskIdStr
  lastStreamEventId = undefined
  connectionState.value = 'CONNECTING'
  connectionLabelOverride.value = undefined
  nowMs.value = Date.now()

  if (!options.skipRouteUpdate) {
    const queryTaskId = resolveQueryTaskId(route.query)
    if (queryTaskId !== taskIdStr) {
      await router.replace({
        query: {
          ...route.query,
          taskId: taskIdStr,
        },
      })
    }
  }

  await loadTaskDetail(taskIdStr)
  await loadAndMergeTaskEvents(taskIdStr)
  lastStreamEventId = resolveLastStreamEventId(events.value)
  startElapsedTimer()

  if (isTerminalTaskStatus(currentTask.value?.taskStatus)) {
    generating.value = false
    connectionState.value = 'COMPLETED'
    connectionLabelOverride.value = undefined
    stopElapsedTimer()
    if (currentTask.value?.taskStatus === 'SUCCESS') {
      await loadFiles()
    }
    if (currentTask.value?.taskStatus === 'FAILED') {
      message.error(progressViewModel.value.failureMessage || '生成任务失败')
    }
    return { terminal: true }
  }

  generating.value = true
  return { terminal: false }
}

const observeGenerationTask = async (taskId: LongId, options: { skipRouteUpdate?: boolean } = {}) => {
  const taskIdStr = normalizeTaskId(taskId)

  if (shouldSkipDuplicateObservation(observedTaskId, taskIdStr, recoveryController.isObservingTask(taskIdStr))) {
    return
  }

  const binding = await bindGenerationTask(taskIdStr, options)
  if (!binding.terminal) {
    startGenerationTaskObservation(taskIdStr)
  }
}

const mergeEvent = (event: TimelineEvent) => {
  if (event.id && /^\d+$/.test(event.id)) {
    lastStreamEventId = event.id
  }

  if (event.eventType === 'MODEL_DELTA') {
    const attempt = Number(event.data?.attempt ?? 1)
    const currentLatest = resolveLatestModelProgress(events.value)
    if (currentLatest && currentLatest.attempt !== attempt) {
      previousModelAttempt.value = currentLatest.attempt
    }
  }

  events.value = mergeTimelineEvent(events.value, event)
}

const applyTimelineEventSideEffects = (event: TimelineEvent) => {
  const taskId = currentTaskId.value ? String(currentTaskId.value) : ''

  if (
    event.eventType === 'TASK_STARTED' ||
    event.eventType === 'PROMPT_RENDERED' ||
    event.eventType === 'FILES_GENERATED' ||
    event.eventType === 'VERSION_CREATED'
  ) {
    currentTask.value = {
      ...(currentTask.value || {
        id: taskId,
        workspaceId: appInfo.value?.workspaceId || '',
        appId,
        taskType: 'RULE_GENERATION',
        taskStatus: 'RUNNING',
      }),
      taskStatus: 'RUNNING',
    }
  }

  if (event.eventType === 'TASK_SUCCESS') {
    currentTask.value = {
      ...(currentTask.value || {
        id: taskId,
        workspaceId: appInfo.value?.workspaceId || '',
        appId,
        taskType: 'RULE_GENERATION',
      }),
      taskStatus: 'SUCCESS',
    }
  }

  if (event.eventType === 'TASK_FAILED') {
    currentTask.value = {
      ...(currentTask.value || {
        id: taskId,
        workspaceId: appInfo.value?.workspaceId || '',
        appId,
        taskType: 'RULE_GENERATION',
      }),
      taskStatus: 'FAILED',
      errorMessage: event.eventMessage,
    }
  }

  if (event.eventType === 'TASK_CANCELLED') {
    currentTask.value = {
      ...(currentTask.value || {
        id: taskId,
        workspaceId: appInfo.value?.workspaceId || '',
        appId,
        taskType: 'RULE_GENERATION',
      }),
      taskStatus: 'CANCELLED',
    }
  }
}

const extractDataField = (data: Record<string, unknown> | undefined, fieldName: string) => {
  const fieldValue = data?.[fieldName]
  return normalizeLongId(fieldValue) ?? undefined
}

const parseEventPayload = (rawPayload?: string): TaskEventPayloadDetail => {
  if (!rawPayload) {
    return {}
  }
  try {
    const parsed = JSON.parse(rawPayload) as TaskEventPayloadDetail | string
    if (typeof parsed === 'string') {
      return JSON.parse(parsed) as TaskEventPayloadDetail
    }
    return parsed
  } catch {
    return {}
  }
}

const findFieldValue = (source: unknown, fieldName: string): unknown => {
  if (!source || typeof source !== 'object') {
    return undefined
  }

  if (Array.isArray(source)) {
    for (const item of source) {
      const nestedValue = findFieldValue(item, fieldName)
      if (nestedValue !== undefined) {
        return nestedValue
      }
    }
    return undefined
  }

  const record = source as Record<string, unknown>
  if (fieldName in record) {
    return record[fieldName]
  }

  for (const value of Object.values(record)) {
    const nestedValue = findFieldValue(value, fieldName)
    if (nestedValue !== undefined) {
      return nestedValue
    }
  }

  return undefined
}

const extractLongField = (rawPayload: string | undefined, fieldName: string) => {
  if (!rawPayload) {
    return undefined
  }
  let parsedPayload: unknown = parseEventPayload(rawPayload)
  try {
    parsedPayload = JSON.parse(rawPayload)
  } catch {
    // keep parsed payload fallback for nested event payload strings
  }
  const fieldValue = findFieldValue(parsedPayload, fieldName)
  if (typeof fieldValue === 'number') {
    return String(fieldValue)
  }
  if (typeof fieldValue === 'string' && /^\d+$/.test(fieldValue)) {
    return fieldValue
  }
  const normalizedPayload = rawPayload.replace(/\\"/g, '"')
  const quotedPattern = new RegExp(`"${fieldName}"\\s*:\\s*"?(\\d+)"?`)
  return normalizedPayload.match(quotedPattern)?.[1]
}

const handleTaskFinished = async (versionId?: LongId) => {
  if (taskFinalized) {
    return
  }
  taskFinalized = true
  recoveryController.finalizeTerminal()
  stopElapsedTimer()
  generating.value = false
  connectionLabelOverride.value = undefined

  if (currentTaskId.value) {
    await syncTerminalTaskState(String(currentTaskId.value))
  }

  const finalVersionId = normalizeLongId(versionId) ?? resolveResultVersionId()
  if (finalVersionId) {
    await loadFiles(finalVersionId)
  } else if (currentTask.value?.taskStatus === 'SUCCESS') {
    await loadFiles()
  }

  if (currentTask.value?.taskStatus === 'SUCCESS') {
    message.success('应用生成成功')
    return
  }

  if (currentTask.value?.taskStatus === 'FAILED') {
    message.error(progressViewModel.value.failureMessage || '应用生成失败')
    return
  }

  if (currentTask.value?.taskStatus === 'CANCELLED') {
    message.warning('生成任务已取消')
  }
}

const createGenerationTaskEntry = async () => {
  const accessToken = getAccessToken()
  if (!accessToken) {
    throw new Error('未登录或登录已过期')
  }

  const response = await fetch(`${API_BASE_URL}/generation-tasks`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      workspaceId: appInfo.value?.workspaceId,
      appId,
      taskType: 'RULE_GENERATION',
      requirement: form.requirement.trim(),
      ...buildGenerationTaskTemplatePayload(selectedTemplate.value),
    }),
  })

  if (!response.ok) {
    throw new Error(`创建生成任务失败: ${response.status}`)
  }

  const rawText = await response.text()
  const parsed = JSON.parse(rawText) as {
    code: number
    message?: string
    data?: {
      workspaceId: LongId
      appId: LongId
      taskType: string
      taskStatus: string
      queuedAt?: string
    }
  }

  if (parsed.code !== 0 || !parsed.data) {
    throw new Error(parsed.message || '创建生成任务失败')
  }

  const taskIdText = extractLongField(rawText, 'taskId')
  if (!taskIdText) {
    throw new Error('创建生成任务失败: 未返回有效 taskId')
  }

  return {
    taskId: taskIdText,
    workspaceId: parsed.data.workspaceId,
    appId: parsed.data.appId,
    taskType: parsed.data.taskType,
    taskStatus: parsed.data.taskStatus,
    queuedAt: parsed.data.queuedAt,
  } satisfies TaskCreationEntry
}

const startGeneration = async () => {
  if (!appInfo.value?.workspaceId) {
    message.error('未找到应用工作空间，无法创建生成任务')
    return
  }
  if (!form.requirement.trim()) {
    message.warning('请输入需求描述')
    return
  }
  if (selectedTemplate.value?.variables.length) {
    const validation = validateTemplateVariableValues(
      selectedTemplate.value.variables,
      selectedTemplate.value.variableValues,
    )
    if (!validation.ok) {
      message.warning(validation.message)
      return
    }
  }

  generating.value = true
  currentTask.value = null
  currentTaskId.value = undefined
  events.value = []
  files.value = []
  previewUrl.value = ''
  observedTaskId = undefined
  lastStreamEventId = undefined
  previousModelAttempt.value = undefined
  lastMilestoneSignature.value = ''
  connectionState.value = 'CONNECTING'
  connectionLabelOverride.value = undefined
  resetTaskFinalization()
  stopObservation()
  stopElapsedTimer()

  let boundTaskId: string | undefined

  try {
    const { created: taskEntry } = await awaitCreationPhase(
      () => createGenerationTaskEntry(),
      async () => {
        const res = await getAiApp(appId)
        if (res.data.code === 0 && res.data.data?.latestTaskId) {
          return res.data.data.latestTaskId
        }
        return undefined
      },
      { timeoutMs: 60_000, intervalMs: 500 },
    )

    const taskIdStr = normalizeTaskId(taskEntry.taskId)
    boundTaskId = taskIdStr

    const binding = await bindGenerationTask(taskIdStr)
    if (!binding.terminal) {
      startGenerationTaskObservation(taskIdStr)
    }
  } catch (error: unknown) {
    const taskIdEstablished = isTaskIdentityEstablished(
      boundTaskId,
      currentTaskId.value ? String(currentTaskId.value) : undefined,
      observedTaskId,
    )
    if (
      shouldRunCreationErrorCleanup({
        taskIdEstablished,
        errorSource: classifyStartGenerationCatchErrorSource(taskIdEstablished),
      })
    ) {
      generating.value = false
      observedTaskId = undefined
      stopObservation()
    }
    message.error((error as Error)?.message || '创建生成任务失败')
  }
}

watch(
  () => buildMilestoneSignature(progressViewModel.value.milestones),
  async (signature) => {
    if (!signature) {
      return
    }
    if (shouldAutoScrollTimeline(lastMilestoneSignature.value, signature)) {
      lastMilestoneSignature.value = signature
      await scrollTimelineToBottom()
    }
  },
)

watch(
  () => resolveQueryTaskId(route.query),
  async (taskId, previousTaskId) => {
    if (!taskId || taskId === previousTaskId) {
      return
    }
    await observeGenerationTask(taskId, { skipRouteUpdate: true })
  },
)

onMounted(async () => {
  await Promise.all([loadApp(), loadTemplatesFromRoute()])
  await loadTaskFromQuery()
})

onUnmounted(() => {
  stopObservation()
  stopElapsedTimer()
})
</script>

<style scoped>
.generate-page { max-width: 1400px; }
.page-toolbar { margin-bottom: 16px; }
.page-heading { margin: 0; font-size: 20px; display: inline; }
.generate-layout { min-height: 500px; }
.panel-card { border-radius: 12px; height: 100%; }
.running-hint { margin-top: 8px; font-size: 12px; color: #6b7280; }
.generation-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.generation-header-title { font-size: 15px; font-weight: 600; color: #111827; }
.generation-header-meta { margin-top: 4px; display: flex; gap: 12px; flex-wrap: wrap; }
.task-id { color: #6b7280; font-size: 12px; }
.elapsed-label { color: #374151; font-size: 12px; }
.generation-header-status { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #374151; white-space: nowrap; }
.connection-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
}
.connection-dot.live { background: #22c55e; animation: pulse-dot 1.8s ease-in-out infinite; }
.connection-dot.polling { background: #f59e0b; }
.connection-dot.syncing { background: #3b82f6; }
@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}
.milestone-timeline { margin-top: 8px; max-height: 520px; overflow-y: auto; }
.milestone-item { display: flex; gap: 12px; margin-bottom: 14px; }
.milestone-marker {
  width: 18px;
  flex-shrink: 0;
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  line-height: 18px;
}
.milestone-completed .milestone-marker { color: #16a34a; }
.milestone-active .milestone-marker { color: #1677ff; }
.milestone-active-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
}
.milestone-label { font-size: 13px; font-weight: 500; color: #111827; }
.milestone-time { font-size: 11px; color: #9ca3af; margin-top: 2px; }
.model-progress-block { margin-top: 6px; }
.model-progress-detail { font-size: 12px; color: #4b5563; }
.retry-attempt-message { font-size: 12px; color: #2563eb; margin-bottom: 4px; }
.waiting-hints { margin-top: 12px; display: grid; gap: 8px; }
.waiting-hint {
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  font-size: 12px;
  color: #4b5563;
}
.result-running { display: flex; align-items: center; gap: 8px; padding: 12px 0; color: #4b5563; }
.success-summary { margin-bottom: 8px; font-size: 12px; color: #6b7280; }
.success-actions, .failed-actions { margin-top: 12px; }
.file-item { padding: 6px 0; border-bottom: 1px solid #f0f0f0; font-size: 13px; display: flex; align-items: center; }
.file-icon { margin-right: 8px; color: #1677ff; }
.error-block { margin-top: 8px; }
.preview-container { height: 520px; border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; }
.preview-frame { width: 100%; height: 100%; border: 0; background: #fff; }
.template-selector-block { display: grid; gap: 8px; }
.selected-template { border: 1px solid var(--cf-border); border-radius: 8px; padding: 8px 10px; font-size: 12px; color: var(--cf-text-secondary); }
.milestone-item.failed .milestone-marker { color: #dc2626; }
</style>
