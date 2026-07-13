<template>
  <PageShell title="提示词模板" description="发现和复用已发布的 AI 生成模板，提高应用生成质量。">
    <div class="template-filters">
      <a-input
        v-model:value="keyword"
        placeholder="搜索模板"
        allow-clear
        style="max-width: 240px"
        @press-enter="loadTemplates"
      />
      <a-select
        v-model:value="scene"
        :options="sceneOptions"
        style="width: 160px"
        @change="loadTemplates"
      />
    </div>

    <a-spin :spinning="loading">
      <a-alert v-if="loadError" type="error" :message="loadError" show-icon style="margin-bottom: 16px" />

      <div v-if="templates.length" class="template-grid">
        <div v-for="item in templates" :key="item.id" class="template-card">
          <div class="template-card-header">
            <h3>{{ item.templateName }}</h3>
            <a-tag color="blue">{{ item.templateSceneLabel }}</a-tag>
          </div>
          <p class="template-card-desc">{{ item.description || '暂无简介' }}</p>
          <div class="template-card-meta">
            <span>适用 {{ item.applicableAppType }}</span>
            <span>·</span>
            <span>v{{ item.currentVersionNo }}</span>
            <span v-if="item.updatedAt">·</span>
            <span v-if="item.updatedAt">{{ formatUpdatedAt(item.updatedAt) }}</span>
          </div>
          <div class="template-card-actions">
            <a-button type="link" size="small" @click="openDetail(item.id)">查看详情</a-button>
            <a-button type="link" size="small" @click="useTemplate(item)">使用模板</a-button>
          </div>
        </div>
      </div>

      <EmptyState
        v-else-if="!loading && !loadError"
        title="暂无已发布模板"
        description="管理员发布模板后，将在这里展示可供复用的提示词模板。"
      />
    </a-spin>

    <PromptTemplateDetailDrawer
      :open="detailOpen"
      :loading="detailLoading"
      :detail="detail"
      @close="detailOpen = false"
      @use="useTemplateFromDetail"
    />
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import PageShell from '@/components/PageShell.vue'
import EmptyState from '@/components/EmptyState.vue'
import PromptTemplateDetailDrawer from '@/components/prompt/PromptTemplateDetailDrawer.vue'
import { getPublishedTemplate, listPublishedTemplates } from '@/api/promptTemplate'
import { buildSceneFilterOptions } from '@/utils/promptTemplateScene'
import { buildHomeRouteWithTemplate } from '@/utils/promptTemplateWorkspace'
import type { PromptTemplateUserDetail, PromptTemplateUserListItem } from '@/utils/promptTemplateTypes'

const router = useRouter()

const keyword = ref('')
const scene = ref('')
const loading = ref(false)
const loadError = ref('')
const templates = ref<PromptTemplateUserListItem[]>([])
const sceneOptions = ref([{ label: '全部', value: '' }])

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<PromptTemplateUserDetail | null>(null)

const formatUpdatedAt = (value: string) => value.replace('T', ' ').slice(0, 16)

const loadTemplates = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listPublishedTemplates({
      keyword: keyword.value.trim() || undefined,
      templateScene: scene.value || undefined,
      pageNo: 1,
      pageSize: 50,
    })
    if (res.data.code !== 0) {
      loadError.value = res.data.message || '加载模板失败'
      templates.value = []
      return
    }
    templates.value = res.data.data?.records || []
    sceneOptions.value = buildSceneFilterOptions(templates.value.map((item) => item.templateScene))
  } catch (error: any) {
    loadError.value = error?.message || '加载模板失败'
    templates.value = []
  } finally {
    loading.value = false
  }
}

const openDetail = async (templateId: number) => {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getPublishedTemplate(templateId)
    if (res.data.code !== 0 || !res.data.data) {
      message.error(res.data.message || '加载模板详情失败')
      return
    }
    detail.value = res.data.data
  } catch (error: any) {
    message.error(error?.message || '加载模板详情失败')
  } finally {
    detailLoading.value = false
  }
}

const navigateWithTemplate = (templateId: number, templateVersionId: number) => {
  router.push(buildHomeRouteWithTemplate(templateId, templateVersionId))
}

const useTemplate = (item: PromptTemplateUserListItem) => {
  if (!item.publishedVersionId) {
    message.warning('模板尚未发布可用版本')
    return
  }
  navigateWithTemplate(item.id, item.publishedVersionId)
}

const useTemplateFromDetail = (value: PromptTemplateUserDetail) => {
  navigateWithTemplate(value.id, value.publishedVersion.id)
  detailOpen.value = false
}

onMounted(() => {
  void loadTemplates()
})
</script>

<style scoped>
.template-filters { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.template-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.template-card { border: 1px solid var(--cf-border); border-radius: 12px; padding: 14px; background: var(--cf-surface); }
.template-card-header { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
.template-card-header h3 { margin: 0; font-size: 15px; line-height: 1.4; }
.template-card-desc { margin: 10px 0; color: var(--cf-text-secondary); font-size: 13px; min-height: 40px; }
.template-card-meta { color: var(--cf-text-tertiary); font-size: 12px; display: flex; gap: 6px; flex-wrap: wrap; }
.template-card-actions { margin-top: 8px; display: flex; gap: 4px; }
</style>
