<template>
  <div class="chat-workbench">
    <div class="chat-scroll">
      <div class="chat-inner">
        <!-- Welcome -->
        <div v-if="messages.length === 0" class="welcome-area">
          <h1 class="welcome-title">你想生成什么应用？</h1>
          <p class="welcome-desc">用一句话描述你的需求，CodeForge AI 会帮你生成应用结构、页面和代码文件。</p>
          <p class="welcome-examples">示例需求（快捷填充文本，非提示词模板）</p>
          <div class="prompt-cards">
            <div
              v-for="p in suggestions"
              :key="p.key"
              :class="['prompt-card', { 'prompt-card-active': selectedPresetKey === p.key }]"
              @click="selectPreset(p)"
            >
              <div class="prompt-label">{{ p.label }}</div>
              <div class="prompt-desc">{{ p.prompt }}</div>
            </div>
          </div>
        </div>

        <!-- Messages -->
        <div v-else class="chat-msgs">
          <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role === 'user' ? 'msg-user' : 'msg-sys']">
            <div class="msg-bubble">
              <div class="msg-text">{{ m.content }}</div>
              <div class="msg-time">{{ m.time }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input bar -->
    <div class="chat-input-wrap">
      <div class="chat-input-card">
        <textarea
          v-model="requirement"
          class="chat-textarea"
          rows="2"
          placeholder="描述你想生成的应用..."
          @keydown="onKeydown"
        ></textarea>
        <div class="input-row">
          <button class="template-btn" type="button" @click="templateDrawerOpen = true">选择模板</button>
          <button class="generate-btn" :disabled="!requirement.trim() || generating" @click="doGenerate">
            <ThunderboltOutlined />
            <span>生成应用</span>
          </button>
        </div>
        <div v-if="selectedTemplate" class="selected-template">
          <span>已选择：{{ selectedTemplate.templateName }} · v{{ selectedTemplate.versionNo }}</span>
          <button type="button" class="link-btn" @click="clearSelectedTemplate">清除</button>
        </div>
        <div v-if="selectedTemplate?.variables.length" class="variable-form">
          <div v-for="variable in selectedTemplate.variables" :key="variable.key" class="variable-row">
            <label>{{ variableFieldLabel(variable.key) }}</label>
            <input
              v-model="selectedTemplate.variableValues[variable.key]"
              class="variable-input"
              :placeholder="variable.description || variable.key"
            />
          </div>
        </div>
      </div>
    </div>

    <TemplateSelectorDrawer
      :open="templateDrawerOpen"
      @close="templateDrawerOpen = false"
      @select="applySelectedTemplate"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import { createAiApp, type AiAppDetailResponse } from '@/api/app'
import { getPublishedTemplate } from '@/api/promptTemplate'
import { getOrCreateDefaultWorkspace, listWorkspaces, type WorkspaceSummaryResponse } from '@/api/workspace'
import { createGenerationTask } from '@/api/task'
import TemplateSelectorDrawer from '@/components/prompt/TemplateSelectorDrawer.vue'
import type { LongId } from '@/types/id'
import {
  applyWorkspacePreset,
  deriveAppDisplayName,
  type WorkspacePreset,
} from '@/utils/deriveAppDisplayName'
import {
  DEFAULT_HOME_APP_TYPE,
  HOME_WORKSPACE_WARNING_KEY,
  resolveWorkspaceIdAfterBootstrap,
  validateHomeGenerationSubmit,
} from '@/pages/homeGenerateContext'
import {
  buildGenerationTaskTemplatePayload,
  resolveRouteTemplateId,
  resolveRouteTemplateVersionId,
} from '@/utils/promptTemplateWorkspace'
import { validateTemplateVariableValues, variableFieldLabel } from '@/utils/promptTemplateVariables'
import type { SelectedPromptTemplate } from '@/utils/promptTemplateTypes'

const route = useRoute()
const router = useRouter()
const emit = defineEmits<{ 'updateApp': [app: AiAppDetailResponse] }>()

const templateDrawerOpen = ref(false)
const selectedTemplate = ref<SelectedPromptTemplate | null>(null)
const requirement = ref('')
const appType = ref(DEFAULT_HOME_APP_TYPE)
const selectedPresetKey = ref<string>()
const workspaceId = ref<LongId>()
const generating = ref(false)
const workspaces = ref<WorkspaceSummaryResponse[]>([])
const messages = ref<{ role: string; content: string; time: string }[]>([])

const suggestions: WorkspacePreset[] = [
  {
    key: 'crm',
    label: '客户管理后台',
    prompt: '生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。',
    appType: 'ADMIN_WEB',
  },
  {
    key: 'blog',
    label: '个人博客',
    prompt: '生成一个个人博客系统，包含文章列表、文章详情和分类标签功能。',
    appType: 'BLOG',
  },
  {
    key: 'ticket',
    label: '工单系统',
    prompt: '生成一个工单管理系统，包含工单提交、工单流转和状态看板。',
    appType: 'ADMIN_WEB',
  },
  {
    key: 'mall',
    label: '在线商城',
    prompt: '生成一个在线商城后台，包含商品管理、订单管理和用户管理。',
    appType: 'ADMIN_WEB',
  },
]

const selectPreset = (preset: WorkspacePreset) => {
  selectedTemplate.value = null
  const applied = applyWorkspacePreset(preset)
  selectedPresetKey.value = applied.selectedPresetKey
  requirement.value = applied.requirement
  appType.value = applied.appType
}

const addMsg = (role: string, content: string) => messages.value.push({ role, content, time: new Date().toLocaleTimeString('zh-CN') })

const applySelectedTemplate = (selection: SelectedPromptTemplate) => {
  selectedTemplate.value = {
    ...selection,
    variableValues: { ...selection.variableValues },
  }
}

const clearSelectedTemplate = () => {
  selectedTemplate.value = null
}

const loadTemplateFromRoute = async () => {
  const templateIdText = resolveRouteTemplateId(route.query)
  const templateVersionIdText = resolveRouteTemplateVersionId(route.query)
  if (!templateIdText) return
  const templateId = Number(templateIdText)
  if (!Number.isFinite(templateId)) return
  try {
    const res = await getPublishedTemplate(templateId)
    if (res.data.code !== 0 || !res.data.data) return
    const detail = res.data.data
    const versionId = templateVersionIdText
      ? Number(templateVersionIdText)
      : detail.publishedVersion.id
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
    selectedPresetKey.value = undefined
    if (detail.applicableAppType) {
      appType.value = detail.applicableAppType
    }
  } catch {
    // keep page usable
  }
}

const loadWorkspaceContext = async () => {
  let listed: WorkspaceSummaryResponse[] = []
  try {
    const res = await listWorkspaces({ pageNo: 1, pageSize: 50 })
    if (res.data.code === 0 && res.data.data) {
      listed = res.data.data.records || []
    }
  } catch {
    listed = []
  }

  let bootstrappedId: string | undefined
  if (listed.length === 0) {
    try {
      const bootstrapRes = await getOrCreateDefaultWorkspace()
      if (bootstrapRes.data.code === 0 && bootstrapRes.data.data?.id) {
        bootstrappedId = String(bootstrapRes.data.data.id)
        listed = [bootstrapRes.data.data]
      }
    } catch {
      // surfaced on generate submit
    }
  }

  workspaces.value = listed
  workspaceId.value = resolveWorkspaceIdAfterBootstrap({
    listedWorkspaces: listed,
    bootstrappedWorkspaceId: bootstrappedId,
  })
}

const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); doGenerate() }
}

const doGenerate = async () => {
  if (generating.value) {
    return
  }
  const req = requirement.value.trim()
  const submitValidation = validateHomeGenerationSubmit({
    workspaceId: workspaceId.value,
    requirement: req,
  })
  if (!submitValidation.ok) {
    message.warning({ content: submitValidation.message, key: HOME_WORKSPACE_WARNING_KEY })
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

  const appName = deriveAppDisplayName(req, appType.value)
  generating.value = true
  addMsg('user', req)
  try {
    addMsg('sys', '正在创建应用...')
    const appRes = await createAiApp({
      workspaceId: workspaceId.value!,
      name: appName,
      description: req,
      appType: appType.value,
    })
    if (appRes.data.code !== 0 || !appRes.data.data) {
      addMsg('sys', `创建失败：${appRes.data.message}`)
      return
    }
    const app = appRes.data.data
    emit('updateApp', app)
    addMsg('sys', `应用「${app.name}」创建成功`)

    addMsg('sys', '正在提交生成任务...')
    const taskRes = await createGenerationTask({
      workspaceId: workspaceId.value!,
      appId: app.id,
      taskType: 'RULE_GENERATION',
      requirement: req,
      ...buildGenerationTaskTemplatePayload(selectedTemplate.value),
    })
    if (taskRes.data.code === 0 && taskRes.data.data) {
      const taskId = String(taskRes.data.data.taskId)
      addMsg('sys', `生成任务已提交（ID: ${taskId}），正在进入生成页...`)
      await router.push(`/apps/${app.id}/generate?taskId=${taskId}`)
      return
    }
    addMsg('sys', `生成任务提交失败：${taskRes.data.message || '请重试'}`)
  } catch (e: unknown) {
    addMsg('sys', `错误：${(e as Error)?.message || '未知'}`)
  } finally {
    generating.value = false
    requirement.value = ''
    selectedPresetKey.value = undefined
    selectedTemplate.value = null
  }
}

onMounted(async () => {
  await loadWorkspaceContext()
  await loadTemplateFromRoute()
})
</script>

<style scoped>
.chat-workbench { height: 100%; display: flex; flex-direction: column; overflow: hidden; min-width: 0; }
.chat-scroll { flex: 1; overflow-y: auto; padding: 40px 24px 120px; min-width: 0; }
.chat-inner { max-width: 860px; margin: 0 auto; }

.welcome-area { display: flex; flex-direction: column; align-items: center; padding: 80px 0 40px; }
.welcome-title { font-size: 26px; font-weight: 700; color: var(--cf-text); margin: 0 0 6px; }
.welcome-desc { font-size: 14px; color: var(--cf-text-secondary); margin: 0 0 28px; text-align: center; max-width: 460px; }
.welcome-examples { font-size: 12px; color: var(--cf-text-tertiary); margin: 0 0 10px; width: 100%; text-align: left; }
.prompt-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; }
.prompt-card { background: var(--cf-surface); border: 1px solid var(--cf-border); border-radius: var(--cf-radius-md); padding: 14px; cursor: pointer; transition: border-color 0.15s; }
.prompt-card:hover { border-color: var(--cf-text); }
.prompt-card-active { border-color: var(--cf-text); background: var(--cf-surface-hover, rgba(0, 0, 0, 0.02)); }
.prompt-label { font-weight: 600; font-size: 13px; color: var(--cf-text); margin-bottom: 3px; }
.prompt-desc { font-size: 12px; color: var(--cf-text-tertiary); }

.chat-msgs { display: flex; flex-direction: column; gap: 16px; }
.msg { display: flex; }
.msg-user { justify-content: flex-end; }
.msg-sys { justify-content: flex-start; }
.msg-bubble { max-width: 78%; border-radius: 12px; padding: 10px 14px; font-size: 14px; line-height: 1.5; }
.msg-user .msg-bubble { background: var(--cf-primary); color: var(--cf-text-inverse); }
.msg-sys .msg-bubble { background: var(--cf-surface); border: 1px solid var(--cf-border); color: var(--cf-text); }
.msg-text { white-space: pre-wrap; word-break: break-word; }
.msg-time { font-size: 11px; margin-top: 4px; opacity: 0.6; }

.chat-input-wrap { position: sticky; bottom: 0; padding: 0 24px 24px; background: linear-gradient(to top, var(--cf-bg) 60%, transparent); }
.chat-input-card { max-width: 860px; margin: 0 auto; background: var(--cf-surface); border: 1px solid var(--cf-border); border-radius: 18px; box-shadow: 0 0 0 1px rgba(0,0,0,0.02), 0 4px 16px rgba(0,0,0,0.04); padding: 10px 14px 10px 18px; transition: border-color 0.15s; }
.chat-input-card:focus-within { border-color: var(--cf-primary); box-shadow: 0 0 0 2px rgba(17,17,17,0.06), 0 4px 20px rgba(0,0,0,0.06); }
.chat-textarea { width: 100%; border: none; outline: none; resize: none; font-size: 14px; font-family: inherit; color: var(--cf-text); background: transparent; line-height: 1.6; min-height: 48px; max-height: 160px; overflow-y: auto; }
.chat-textarea::placeholder { color: var(--cf-text-tertiary); }
.input-row { display: flex; justify-content: flex-end; align-items: center; margin-top: 6px; gap: 10px; }
.template-btn { height: 34px; border-radius: 10px; border: 1px solid var(--cf-border); background: #fff; color: var(--cf-text); font-size: 13px; padding: 0 14px; cursor: pointer; }
.selected-template { margin-top: 8px; font-size: 12px; color: var(--cf-text-secondary); display: flex; justify-content: space-between; gap: 8px; }
.link-btn { border: none; background: transparent; color: var(--cf-primary); cursor: pointer; font-size: 12px; }
.variable-form { margin-top: 8px; display: grid; gap: 6px; }
.variable-row { display: grid; gap: 4px; }
.variable-row label { font-size: 12px; color: var(--cf-text-secondary); }
.variable-input { border: 1px solid var(--cf-border); border-radius: 8px; padding: 6px 10px; font-size: 13px; }
.generate-btn { height: 34px; border-radius: 10px; background: var(--cf-primary); border: none; color: #fff; font-weight: 500; font-size: 13px; display: flex; align-items: center; gap: 6px; padding: 0 16px; cursor: pointer; white-space: nowrap; flex-shrink: 0; }
.generate-btn:hover:not(:disabled) { background: var(--cf-primary-hover); }
.generate-btn:disabled { opacity: 0.45; cursor: not-allowed; }
</style>
