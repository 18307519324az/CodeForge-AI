<template>
  <a-drawer :open="open" title="选择提示词模板" width="640" @close="handleClose">
    <template v-if="!pendingTemplate">
      <div class="selector-toolbar">
        <a-input v-model:value="keyword" allow-clear placeholder="搜索模板" @press-enter="loadTemplates" />
        <a-select
          v-model:value="scene"
          :options="sceneOptions"
          style="width: 160px"
          @change="loadTemplates"
        />
        <a-button @click="loadTemplates">搜索</a-button>
      </div>

      <a-spin :spinning="loading">
        <div v-if="templates.length" class="template-grid">
          <div v-for="item in templates" :key="item.id" class="template-card">
            <div class="template-card-head">
              <h4>{{ item.templateName }}</h4>
              <a-tag>{{ item.templateSceneLabel }}</a-tag>
            </div>
            <p>{{ item.description || '暂无简介' }}</p>
            <div class="template-card-meta">
              <span>适用 {{ item.applicableAppType }}</span>
              <span>最新 v{{ item.currentVersionNo }}</span>
            </div>
            <div class="template-card-actions">
              <a-button type="link" size="small" @click="emit('preview', item.id)">预览</a-button>
              <a-button type="link" size="small" @click="openVersionPicker(item)">选择</a-button>
            </div>
          </div>
        </div>
        <a-empty v-else description="暂无已发布模板" />
      </a-spin>
    </template>

    <template v-else>
      <a-button type="link" class="back-link" @click="pendingTemplate = null">← 返回模板列表</a-button>
      <h3 class="version-picker-title">{{ pendingTemplate.templateName }}</h3>
      <p class="version-picker-hint">请选择要用于本次生成的模板版本（不会自动使用最新版本）</p>
      <a-spin :spinning="versionLoading">
        <a-radio-group v-model:value="selectedVersionId" class="version-list">
          <a-radio
            v-for="version in publishedVersions"
            :key="version.id"
            :value="version.id"
            class="version-option"
          >
            <span class="version-label">v{{ version.versionNo }}</span>
            <a-tag v-if="version.id === pendingTemplate.publishedVersionId" color="blue">最新</a-tag>
          </a-radio>
        </a-radio-group>
        <a-empty v-if="!versionLoading && !publishedVersions.length" description="暂无已发布版本" />
      </a-spin>
      <div class="version-picker-actions">
        <a-button @click="pendingTemplate = null">取消</a-button>
        <a-button type="primary" :disabled="!selectedVersionId" :loading="confirming" @click="confirmVersionSelection">
          确认选择
        </a-button>
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  getPublishedTemplate,
  listPublishedTemplateVersions,
  listPublishedTemplates,
} from '@/api/promptTemplate'
import { buildSceneFilterOptions } from '@/utils/promptTemplateScene'
import type {
  PromptTemplatePublishedVersion,
  PromptTemplateUserListItem,
  SelectedPromptTemplate,
} from '@/utils/promptTemplateTypes'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  close: []
  preview: [templateId: number]
  select: [selection: SelectedPromptTemplate]
}>()

const keyword = ref('')
const scene = ref('')
const loading = ref(false)
const templates = ref<PromptTemplateUserListItem[]>([])
const sceneOptions = ref([{ label: '全部', value: '' }])

const pendingTemplate = ref<PromptTemplateUserListItem | null>(null)
const publishedVersions = ref<PromptTemplatePublishedVersion[]>([])
const selectedVersionId = ref<number | null>(null)
const versionLoading = ref(false)
const confirming = ref(false)

const resetVersionPicker = () => {
  pendingTemplate.value = null
  publishedVersions.value = []
  selectedVersionId.value = null
}

const handleClose = () => {
  resetVersionPicker()
  emit('close')
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await listPublishedTemplates({
      keyword: keyword.value.trim() || undefined,
      templateScene: scene.value || undefined,
      pageNo: 1,
      pageSize: 50,
    })
    if (res.data.code !== 0) {
      message.error(res.data.message || '加载模板失败')
      return
    }
    templates.value = res.data.data?.records || []
    sceneOptions.value = buildSceneFilterOptions(templates.value.map((item) => item.templateScene))
  } catch (error: any) {
    message.error(error?.message || '加载模板失败')
  } finally {
    loading.value = false
  }
}

const openVersionPicker = async (item: PromptTemplateUserListItem) => {
  pendingTemplate.value = item
  selectedVersionId.value = item.publishedVersionId ?? null
  versionLoading.value = true
  try {
    const res = await listPublishedTemplateVersions(item.id)
    if (res.data.code !== 0) {
      message.error(res.data.message || '加载模板版本失败')
      pendingTemplate.value = null
      return
    }
    publishedVersions.value = res.data.data || []
    if (!selectedVersionId.value && publishedVersions.value.length) {
      selectedVersionId.value = publishedVersions.value[0].id
    }
  } catch (error: any) {
    message.error(error?.message || '加载模板版本失败')
    pendingTemplate.value = null
  } finally {
    versionLoading.value = false
  }
}

const confirmVersionSelection = async () => {
  if (!pendingTemplate.value || !selectedVersionId.value) {
    return
  }
  confirming.value = true
  try {
    const res = await getPublishedTemplate(pendingTemplate.value.id)
    if (res.data.code !== 0 || !res.data.data) {
      message.error(res.data.message || '加载模板详情失败')
      return
    }
    const detail = res.data.data
    const chosen = publishedVersions.value.find((version) => version.id === selectedVersionId.value)
    if (!chosen) {
      message.error('所选模板版本不可用')
      return
    }
    emit('select', {
      templateId: detail.id,
      templateVersionId: chosen.id,
      templateName: detail.templateName,
      versionNo: chosen.versionNo,
      applicableAppType: detail.applicableAppType,
      variables: detail.variables,
      variableValues: {},
    })
    resetVersionPicker()
    emit('close')
  } catch (error: any) {
    message.error(error?.message || '加载模板详情失败')
  } finally {
    confirming.value = false
  }
}

watch(
  () => props.open,
  (visible) => {
    if (visible) {
      resetVersionPicker()
      void loadTemplates()
    }
  },
)

onMounted(() => {
  if (props.open) {
    void loadTemplates()
  }
})
</script>

<style scoped>
.selector-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.template-grid { display: grid; gap: 10px; }
.template-card { border: 1px solid var(--cf-border); border-radius: 10px; padding: 12px; }
.template-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.template-card-head h4 { margin: 0; font-size: 14px; }
.template-card p { margin: 8px 0; color: var(--cf-text-secondary); font-size: 12px; min-height: 32px; }
.template-card-meta { display: flex; gap: 12px; color: var(--cf-text-tertiary); font-size: 12px; }
.template-card-actions { margin-top: 8px; }
.back-link { padding-left: 0; margin-bottom: 8px; }
.version-picker-title { margin: 0 0 4px; font-size: 16px; }
.version-picker-hint { margin: 0 0 12px; color: var(--cf-text-secondary); font-size: 12px; }
.version-list { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.version-option { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid var(--cf-border); border-radius: 8px; width: 100%; }
.version-label { font-weight: 500; }
.version-picker-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
