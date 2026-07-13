<template>
  <div id="appChatPage">
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.name || '应用任务中心' }}</h1>
        <a-tag v-if="appInfo?.appType" color="blue">{{ appInfo.appType }}</a-tag>
        <a-tag v-if="currentTask?.taskStatus" color="processing">{{ currentTask.taskStatus }}</a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">应用详情</a-button>
        <a-button
          type="default"
          @click="createExport"
          :loading="exporting"
          :disabled="!appInfo?.currentVersionId"
        >
          创建导出包
        </a-button>
        <a-button
          type="primary"
          @click="deployCurrentVersion"
          :loading="deploying"
          :disabled="!appInfo?.currentVersionId"
        >
          创建部署任务
        </a-button>
      </div>
    </div>

    <div class="main-content">
      <div class="chat-section">
        <div class="messages-container" ref="messagesContainer">
          <div
            v-for="(message, index) in displayMessages"
            :key="`${message.type}-${index}-${message.createdAt || ''}`"
            class="message-item"
          >
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser?.avatarUrl" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="CODEFORGE_MASCOT_URL" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.markdown" :content="message.content" />
                <template v-else>{{ message.content }}</template>
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>任务执行中...</span>
                </div>
              </div>
            </div>
          </div>
          <a-empty v-if="!displayMessages.length" description="还没有生成记录，先输入需求创建任务" />
        </div>

        <div class="task-events">
          <div class="task-events-header">
            <h3>任务事件</h3>
            <a-space v-if="currentTaskId">
              <a-button
                size="small"
                @click="cancelTask"
                :disabled="!canCancelCurrentTask"
                :loading="taskActionLoading"
              >
                取消任务
              </a-button>
              <a-button
                size="small"
                @click="retryTask"
                :disabled="!canRetryCurrentTask"
                :loading="taskActionLoading"
              >
                重试任务
              </a-button>
            </a-space>
          </div>
          <a-timeline v-if="taskEvents.length">
            <a-timeline-item v-for="event in taskEvents" :key="event.eventId">
              <div class="event-title">{{ event.type }}</div>
              <div class="event-message">{{ event.message }}</div>
              <div class="event-time">{{ formatTime(event.timestamp) }}</div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="当前没有任务事件" />
        </div>

        <div class="input-container">
          <div class="input-wrapper">
            <a-textarea
              v-model:value="userInput"
              :placeholder="appInfo ? '描述你要生成或修改的应用内容' : '正在加载应用信息...'"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating || !appInfo"
            />
            <div class="input-actions">
              <a-button type="primary" @click="sendMessage" :loading="isGenerating" :disabled="!appInfo">
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>

      <div class="preview-section">
        <div class="preview-header">
          <h3>版本预览</h3>
          <div class="preview-actions">
            <span v-if="appInfo?.currentVersionId">当前版本 ID: {{ appInfo.currentVersionId }}</span>
          </div>
        </div>
        <div class="preview-content">
          <div v-if="previewLoading" class="preview-loading">
            <a-spin size="large" />
            <p>正在加载版本内容...</p>
          </div>
          <div v-else-if="latestVersionContent" class="preview-markdown">
            <MarkdownRenderer :content="latestVersionContent" />
          </div>
          <a-empty v-else description="当前版本还没有可预览文件" />
        </div>

        <div class="side-panels">
          <a-card size="small" title="导出记录">
            <a-list
              v-if="exportPackages.length"
              :data-source="exportPackages"
              size="small"
              bordered
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <div>
                    <div>{{ item.packageType }} · {{ item.status }}</div>
                    <div class="side-note">#{{ item.id }} · {{ item.fileName }}</div>
                  </div>
                </a-list-item>
              </template>
            </a-list>
            <a-empty v-else description="暂无导出记录" />
          </a-card>

          <a-card size="small" title="部署任务" style="margin-top: 16px">
            <div v-if="deploymentDetail">
              <div>{{ deploymentDetail.deployTarget }} · {{ deploymentDetail.deployStatus }}</div>
              <div class="side-note">requestId: {{ deploymentDetail.requestId }}</div>
              <a-list
                v-if="deploymentLogs.length"
                :data-source="deploymentLogs"
                size="small"
                bordered
                style="margin-top: 12px"
              >
                <template #renderItem="{ item }">
                  <a-list-item>
                    <div>
                      <div>{{ item.logLevel }} · {{ item.logMessage }}</div>
                      <div class="side-note">{{ formatTime(item.logTime) }}</div>
                    </div>
                  </a-list-item>
                </template>
              </a-list>
            </div>
            <a-empty v-else description="暂无部署任务" />
          </a-card>
        </div>
      </div>
    </div>

    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="true"
      @edit="editApp"
      @delete="deleteApp"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SendOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { deleteAiApp, getAiApp, type AiAppDetailResponse } from '@/api/app'
import {
  cancelGenerationTask,
  createGenerationTask,
  getGenerationTask,
  listGenerationRecords,
  listGenerationTaskEvents,
  retryGenerationTask,
  type GenerationRecordResponse,
  type GenerationTaskDetailResponse,
  type PublicGenerationStreamEvent,
} from '@/api/task'
import {
  getAppVersionFileContent,
  listAppVersionFiles,
  type AppVersionFileResponse,
} from '@/api/appVersion'
import {
  createExportPackage,
  listExportPackages,
  type ExportPackageListItemResponse,
} from '@/api/export'
import {
  createDeployment,
  getDeployment,
  getDeploymentLogs,
  type DeploymentDetailResponse,
  type DeploymentLogResponse,
} from '@/api/deployment'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { CODEFORGE_MASCOT_URL } from '@/constants/brand'
import { formatTime } from '@/utils/time'
import type { LongId } from '@/types/id'

const TASK_TYPE = 'APP_GENERATION'
const EXPORT_PACKAGE_TYPE = 'ZIP'
const DEPLOY_ENVIRONMENT_CODE = 'prod'
const DEPLOY_TARGET = 'docker'
const TERMINAL_TASK_STATUSES = ['SUCCESS', 'FAILED', 'CANCELLED']

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

const appInfo = ref<AiAppDetailResponse>()
const generationRecords = ref<GenerationRecordResponse[]>([])
const taskEvents = ref<PublicGenerationStreamEvent[]>([])
const currentTask = ref<GenerationTaskDetailResponse>()
const currentTaskId = ref<LongId>()
const userInput = ref('')
const pendingRequirement = ref('')
const isGenerating = ref(false)
const previewLoading = ref(false)
const latestVersionContent = ref('')
const latestVersionFile = ref<AppVersionFileResponse>()
const exporting = ref(false)
const deploying = ref(false)
const taskActionLoading = ref(false)
const exportPackages = ref<ExportPackageListItemResponse[]>([])
const deploymentDetail = ref<DeploymentDetailResponse>()
const deploymentLogs = ref<DeploymentLogResponse[]>([])
const appDetailVisible = ref(false)
const messagesContainer = ref<HTMLElement>()

let taskPollTimer: ReturnType<typeof setInterval> | undefined

interface DisplayMessage {
  type: 'user' | 'ai'
  content: string
  createdAt?: string
  markdown: boolean
  loading?: boolean
}

const displayMessages = computed<DisplayMessage[]>(() => {
  const historyMessages = generationRecords.value.flatMap((record): DisplayMessage[] => {
    const items: DisplayMessage[] = []
    if (record.inputSummary) {
      items.push({
        type: 'user' as const,
        content: record.inputSummary,
        createdAt: record.createdAt,
        markdown: false,
      })
    }
    if (record.outputSummary) {
      items.push({
        type: 'ai' as const,
        content: record.outputSummary,
        createdAt: record.createdAt,
        markdown: true,
      })
    }
    return items
  })

  if (pendingRequirement.value) {
    historyMessages.push({
      type: 'user' as const,
      content: pendingRequirement.value,
      createdAt: '',
      markdown: false,
    })
    historyMessages.push({
      type: 'ai' as const,
      content: '任务已创建，正在处理中。',
      createdAt: '',
      markdown: false,
      loading: isGenerating.value,
    })
  }
  return historyMessages
})

const canCancelCurrentTask = computed(() => {
  if (!currentTask.value?.taskStatus) {
    return false
  }
  return !TERMINAL_TASK_STATUSES.includes(currentTask.value.taskStatus)
})

const canRetryCurrentTask = computed(() => {
  return currentTask.value?.taskStatus === 'FAILED' || currentTask.value?.taskStatus === 'CANCELLED'
})

const showAppDetail = () => {
  appDetailVisible.value = true
}

const stopTaskPolling = () => {
  if (taskPollTimer) {
    clearInterval(taskPollTimer)
    taskPollTimer = undefined
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const loadGenerationRecords = async () => {
  if (!appInfo.value?.id) {
    return
  }
  const res = await listGenerationRecords(appInfo.value.id)
  if (res.data.code === 0 && res.data.data) {
    generationRecords.value = res.data.data
  }
}

const loadExportPackages = async () => {
  if (!appInfo.value?.id) {
    return
  }
  const res = await listExportPackages(appInfo.value.id)
  if (res.data.code === 0 && res.data.data) {
    exportPackages.value = res.data.data
  }
}

const loadLatestVersionPreview = async () => {
  if (!appInfo.value?.id || !appInfo.value.currentVersionId) {
    latestVersionContent.value = ''
    latestVersionFile.value = undefined
    return
  }

  previewLoading.value = true
  try {
    const filesRes = await listAppVersionFiles(appInfo.value.id, appInfo.value.currentVersionId)
    if (filesRes.data.code !== 0 || !filesRes.data.data.length) {
      latestVersionContent.value = ''
      latestVersionFile.value = undefined
      return
    }

    const preferredFile =
      filesRes.data.data.find((file) => file.fileName === 'result.md') || filesRes.data.data[0]
    latestVersionFile.value = preferredFile

    const contentRes = await getAppVersionFileContent(
      appInfo.value.id,
      appInfo.value.currentVersionId,
      preferredFile.filePath,
    )
    if (contentRes.data.code === 0 && contentRes.data.data) {
      latestVersionContent.value = contentRes.data.data.content
    }
  } catch (error) {
    console.error('加载版本预览失败', error)
    latestVersionContent.value = ''
  } finally {
    previewLoading.value = false
  }
}

const loadTaskState = async (taskId: LongId) => {
  const [taskRes, eventsRes] = await Promise.all([getGenerationTask(taskId), listGenerationTaskEvents(taskId)])
  if (taskRes.data.code === 0 && taskRes.data.data) {
    currentTask.value = taskRes.data.data
    currentTaskId.value = taskRes.data.data.id
  }
  if (eventsRes.data.code === 0 && eventsRes.data.data) {
    taskEvents.value = eventsRes.data.data
  }
}

const refreshAppState = async () => {
  if (!appInfo.value?.id) {
    return
  }
  const appRes = await getAiApp(appInfo.value.id)
  if (appRes.data.code === 0 && appRes.data.data) {
    appInfo.value = appRes.data.data
  }
  await Promise.all([loadGenerationRecords(), loadExportPackages(), loadLatestVersionPreview()])
  await scrollToBottom()
}

const onTaskTerminal = async () => {
  stopTaskPolling()
  isGenerating.value = false
  pendingRequirement.value = ''
  await refreshAppState()
}

const startTaskPolling = async (taskId: LongId) => {
  stopTaskPolling()
  await loadTaskState(taskId)
  if (currentTask.value?.taskStatus && TERMINAL_TASK_STATUSES.includes(currentTask.value.taskStatus)) {
    await onTaskTerminal()
    return
  }

  taskPollTimer = setInterval(async () => {
    await loadTaskState(taskId)
    if (currentTask.value?.taskStatus && TERMINAL_TASK_STATUSES.includes(currentTask.value.taskStatus)) {
      await onTaskTerminal()
    }
  }, 2000)
}

const fetchAppInfo = async () => {
  const appId = String(route.params.id ?? '')
  if (!appId) {
    message.error('应用 ID 不存在')
    await router.push('/')
    return
  }

  try {
    const res = await getAiApp(appId)
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      await Promise.all([loadGenerationRecords(), loadExportPackages(), loadLatestVersionPreview()])
      return
    }
    message.error(`获取应用失败：${res.data.message}`)
    await router.push('/')
  } catch (error) {
    console.error('获取应用失败', error)
    message.error('获取应用失败')
    await router.push('/')
  }
}

const sendMessage = async () => {
  if (!appInfo.value?.id || !userInput.value.trim() || isGenerating.value) {
    return
  }

  const requirement = userInput.value.trim()
  userInput.value = ''
  pendingRequirement.value = requirement
  isGenerating.value = true
  await scrollToBottom()

  try {
    const res = await createGenerationTask({
      workspaceId: appInfo.value.workspaceId,
      appId: appInfo.value.id,
      taskType: TASK_TYPE,
      requirement,
      idempotencyKey: `task_${Date.now()}_${appInfo.value.id}`,
    })
    if (res.data.code === 0 && res.data.data) {
      message.success('生成任务已创建')
      await startTaskPolling(res.data.data.taskId)
      return
    }
    pendingRequirement.value = ''
    isGenerating.value = false
    message.error(`创建任务失败：${res.data.message}`)
  } catch (error) {
    console.error('创建任务失败', error)
    pendingRequirement.value = ''
    isGenerating.value = false
    message.error('创建任务失败')
  }
}

const cancelTask = async () => {
  if (!currentTaskId.value) {
    return
  }
  taskActionLoading.value = true
  try {
    const res = await cancelGenerationTask(currentTaskId.value)
    if (res.data.code === 0 && res.data.data) {
      currentTask.value = res.data.data
      await loadTaskState(currentTaskId.value)
      await onTaskTerminal()
      message.success('任务已取消')
      return
    }
    message.error(`取消任务失败：${res.data.message}`)
  } catch (error) {
    console.error('取消任务失败', error)
    message.error('取消任务失败')
  } finally {
    taskActionLoading.value = false
  }
}

const retryTask = async () => {
  if (!currentTaskId.value) {
    return
  }
  taskActionLoading.value = true
  try {
    const res = await retryGenerationTask(currentTaskId.value)
    if (res.data.code === 0 && res.data.data) {
      pendingRequirement.value = '任务已重试'
      isGenerating.value = true
      await startTaskPolling(res.data.data.taskId)
      message.success('任务已重新入队')
      return
    }
    message.error(`重试任务失败：${res.data.message}`)
  } catch (error) {
    console.error('重试任务失败', error)
    message.error('重试任务失败')
  } finally {
    taskActionLoading.value = false
  }
}

const createExport = async () => {
  if (!appInfo.value?.id || !appInfo.value.currentVersionId) {
    message.warning('当前没有可导出的版本')
    return
  }
  exporting.value = true
  try {
    const res = await createExportPackage({
      appId: appInfo.value.id,
      appVersionId: appInfo.value.currentVersionId,
      packageType: EXPORT_PACKAGE_TYPE,
    })
    if (res.data.code === 0 && res.data.data) {
      await loadExportPackages()
      message.success(`导出包已创建：${res.data.data.fileName}（#${res.data.data.id}）`)
      return
    }
    message.error(`创建导出包失败：${res.data.message}`)
  } catch (error) {
    console.error('创建导出包失败', error)
    message.error('创建导出包失败')
  } finally {
    exporting.value = false
  }
}

const deployCurrentVersion = async () => {
  if (!appInfo.value?.id || !appInfo.value.currentVersionId) {
    message.warning('当前没有可部署的版本')
    return
  }
  deploying.value = true
  try {
    const res = await createDeployment({
      appId: appInfo.value.id,
      appVersionId: appInfo.value.currentVersionId,
      environmentCode: DEPLOY_ENVIRONMENT_CODE,
      deployTarget: DEPLOY_TARGET,
      runtimeConfigJson: '{"replicas":1}',
    })
    if (res.data.code === 0 && res.data.data) {
      const detailRes = await getDeployment(res.data.data.id)
      if (detailRes.data.code === 0 && detailRes.data.data) {
        deploymentDetail.value = detailRes.data.data
      }
      const logsRes = await getDeploymentLogs(res.data.data.id)
      if (logsRes.data.code === 0 && logsRes.data.data) {
        deploymentLogs.value = logsRes.data.data
      }
      message.success(`部署任务已创建：${res.data.data.requestId}`)
      return
    }
    message.error(`创建部署任务失败：${res.data.message}`)
  } catch (error) {
    console.error('创建部署任务失败', error)
    message.error('创建部署任务失败')
  } finally {
    deploying.value = false
  }
}

const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

const deleteApp = async () => {
  if (!appInfo.value?.id) {
    return
  }
  try {
    const res = await deleteAiApp(appInfo.value.id)
    if (res.data.code === 0) {
      message.success('应用已删除')
      appDetailVisible.value = false
      await router.push('/')
      return
    }
    message.error(`删除应用失败：${res.data.message}`)
  } catch (error) {
    console.error('删除应用失败', error)
    message.error('删除应用失败')
  }
}

onMounted(() => {
  fetchAppInfo()
})

onUnmounted(() => {
  stopTaskPolling()
})
</script>

<style scoped>
#appChatPage {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #f8fafc;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

.chat-section,
.preview-section {
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.chat-section {
  flex: 2;
}

.preview-section {
  flex: 3;
}

.messages-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.message-item {
  margin-bottom: 12px;
}

.user-message,
.ai-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.user-message {
  justify-content: flex-end;
}

.message-content {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1677ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
  margin-top: 8px;
}

.task-events {
  padding: 0 16px 16px;
  border-top: 1px solid #f0f0f0;
  max-height: 260px;
  overflow-y: auto;
}

.task-events-header,
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
}

.task-events-header h3,
.preview-header h3 {
  margin: 0;
}

.event-title {
  font-weight: 600;
}

.event-message,
.event-time,
.side-note {
  color: #666;
  font-size: 12px;
}

.input-container {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
}

.input-wrapper {
  position: relative;
}

.input-actions {
  position: absolute;
  right: 8px;
  bottom: 8px;
}

.preview-content {
  flex: 1;
  padding: 0 16px 16px;
  overflow-y: auto;
}

.preview-loading {
  text-align: center;
  padding: 48px 0;
}

.preview-markdown {
  padding: 8px 0;
}

.side-panels {
  padding: 0 16px 16px;
}

@media (max-width: 1200px) {
  .main-content {
    flex-direction: column;
  }
}
</style>
