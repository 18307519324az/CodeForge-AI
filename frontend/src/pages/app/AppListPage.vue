<template>
  <PageShell title="我的应用" description="管理你创建的 AI 应用原型、版本和生成记录">
    <template #actions>
      <button class="cf-btn-primary" @click="router.push('/apps/create')">
        <PlusOutlined /> 新建应用
      </button>
    </template>

    <div
      class="cf-filter-card"
      v-if="total > 0 || searchKeyword || filterStatus || filterAppType"
    >
      <a-input
        v-model:value="searchKeyword"
        placeholder="搜索应用名称"
        allow-clear
        style="max-width:240px"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="filterStatus"
        placeholder="应用状态"
        allow-clear
        :options="statusOptions"
        style="max-width:150px"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="filterAppType"
        placeholder="应用类型"
        allow-clear
        :options="appTypeOptions"
        style="max-width:150px"
        @change="onFilterChange"
      />
    </div>

    <div v-if="total > 0" class="cf-app-list-summary">共 {{ total }} 个应用</div>

    <a-spin :spinning="loading">
      <a-alert
        v-if="loadError"
        type="error"
        :message="loadError"
        show-icon
        style="margin-bottom: 16px"
      />

      <div v-if="apps.length" class="cf-app-grid">
        <div v-for="app in apps" :key="app.id" class="cf-app-card" @click="router.push(`/apps/${app.id}`)">
          <div class="cf-app-card-header">
            <a-tooltip :title="app.name">
              <div class="cf-app-card-name">{{ app.name }}</div>
            </a-tooltip>
            <a-tag :color="displayStatusColor(app.displayStatus)">
              {{ displayStatusLabel(app.displayStatus) }}
            </a-tag>
          </div>
          <div class="cf-app-card-desc">{{ app.description || '暂无描述' }}</div>
          <div class="cf-app-card-type">{{ appTypeLabel(app.appType) }}</div>
          <div class="cf-app-card-meta">{{ buildCardMeta(app) }}</div>
          <div class="cf-app-card-export">导出：{{ exportStatusLabel(app.latestExportStatus) }}</div>
          <div class="cf-app-card-actions">
            <a-button type="link" size="small" @click.stop="router.push(`/apps/${app.id}/generate`)">
              <ThunderboltOutlined /> AI 生成
            </a-button>
            <a-button
              type="link"
              size="small"
              :disabled="!app.currentVersionId"
              @click.stop="handlePreview(app)"
            >
              <EyeOutlined /> 预览
            </a-button>
            <a-button type="link" size="small" @click.stop="router.push(`/apps/${app.id}`)">详情</a-button>
          </div>
        </div>
      </div>

      <EmptyState
        v-else-if="!loading && !loadError"
        title="暂无应用"
        description="创建你的第一个 AI 应用，开始生成应用原型、版本和文件。"
        action-text="新建应用"
        @action="router.push('/apps/create')"
      />
    </a-spin>

    <div v-if="total > 0" class="cf-app-list-pagination">
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :page-size-options="pageSizeOptions"
        show-size-changer
        show-less-items
        :show-total="false"
        @change="onPageChange"
        @showSizeChange="onPageSizeChange"
      />
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { EyeOutlined, PlusOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { listAiApps, type AiAppListItemResponse } from '@/api/app'
import PageShell from '@/components/PageShell.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  appTypeLabel,
  displayStatusColor,
  displayStatusLabel,
  exportStatusLabel,
  formatVersionNoFromNumber,
  versionSourceLabel,
} from '@/utils/appLabels'
import { openAppVersionPreview } from '@/utils/openAppVersionPreview'
import {
  ALLOWED_PAGE_SIZES,
  DEFAULT_PAGE_SIZE,
  normalizePageSize,
  resolveAppListPage,
} from '@/pages/app/appListPagination'

const router = useRouter()
const loading = ref(false)
const loadError = ref<string>()
const apps = ref<AiAppListItemResponse[]>([])
const searchKeyword = ref('')
const filterStatus = ref<string>()
const filterAppType = ref<string>()
const pageNo = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

const pageSizeOptions = ALLOWED_PAGE_SIZES.map(String)
let latestRequestId = 0

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '开发中', value: 'DEVELOPING' },
  { label: '可发布', value: 'READY_TO_RELEASE' },
  { label: '已发布', value: 'RELEASED' },
  { label: '已归档', value: 'ARCHIVED' },
]

const appTypeOptions = [
  { label: 'Web 应用', value: 'WEB_APP' },
  { label: '后台管理', value: 'ADMIN_WEB' },
  { label: '博客', value: 'BLOG' },
  { label: '官网', value: 'OFFICIAL_SITE' },
]

const buildCardMeta = (app: AiAppListItemResponse) => {
  const parts = [formatVersionNoFromNumber(app.currentVersionNo)]
  if (app.latestGenerationSource) {
    parts.push(versionSourceLabel(app.latestGenerationSource))
  }
  if (app.generatedFileCount != null) {
    parts.push(`${app.generatedFileCount} 个文件`)
  }
  return parts.join(' · ')
}

const handlePreview = async (app: AiAppListItemResponse) => {
  if (!app.currentVersionId) {
    message.warning('暂无可预览版本')
    return
  }
  await openAppVersionPreview(app.id, app.currentVersionId)
}

const loadApps = async () => {
  const requestId = ++latestRequestId
  loading.value = true
  loadError.value = undefined
  try {
    let fetchPageNo = pageNo.value
    for (let attempt = 0; attempt < 2; attempt += 1) {
      const res = await listAiApps({
        keyword: searchKeyword.value || undefined,
        status: filterStatus.value || undefined,
        appType: filterAppType.value || undefined,
        pageNo: fetchPageNo,
        pageSize: pageSize.value,
      })
      if (requestId !== latestRequestId) {
        return
      }
      if (res.data.code !== 0 || !res.data.data) {
        apps.value = []
        total.value = 0
        loadError.value = res.data.message || '加载失败'
        return
      }
      const resolved = resolveAppListPage(fetchPageNo, pageSize.value, res.data.data)
      total.value = resolved.total
      pageSize.value = resolved.pageSize
      pageNo.value = resolved.pageNo
      apps.value = resolved.records as AiAppListItemResponse[]
      if (!resolved.needsRefetch) {
        break
      }
      fetchPageNo = resolved.pageNo
    }
  } catch (e: unknown) {
    if (requestId !== latestRequestId) {
      return
    }
    apps.value = []
    total.value = 0
    const msg = e instanceof Error ? e.message : '加载失败'
    loadError.value = msg
    message.error(msg)
  } finally {
    if (requestId === latestRequestId) {
      loading.value = false
    }
  }
}

const onFilterChange = () => {
  pageNo.value = 1
  loadApps()
}

const onPageChange = (nextPage: number) => {
  pageNo.value = nextPage
  loadApps()
}

const onPageSizeChange = (_current: number, nextSize: number) => {
  pageSize.value = normalizePageSize(nextSize)
  pageNo.value = 1
  loadApps()
}

onMounted(() => loadApps())
</script>

<style scoped>
.cf-app-list-summary {
  margin-bottom: 12px;
  color: var(--cf-text-secondary, #64748b);
  font-size: 14px;
}

.cf-app-list-pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-bottom: 8px;
}

.cf-app-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 4px;
}

.cf-app-card-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cf-app-card-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}

.cf-app-card-type {
  font-size: 12px;
  color: var(--cf-text-tertiary, #94a3b8);
  margin: 8px 0 4px;
}

.cf-app-card-export {
  font-size: 12px;
  color: var(--cf-text-secondary, #64748b);
  margin-bottom: 8px;
}
</style>
