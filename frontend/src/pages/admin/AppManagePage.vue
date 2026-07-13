<template>
  <AdminPageShell>
    <div class="app-manage-page">
      <header class="page-header">
        <div>
          <h2 class="page-heading">应用管理</h2>
          <p class="page-subtitle">查看与管理平台全部 AI 应用</p>
        </div>
        <a-space wrap class="toolbar-actions">
          <a-input
            v-model:value="keyword"
            allow-clear
            class="search-input"
            placeholder="搜索应用名称或描述"
            @press-enter="reload"
          />
          <a-button type="primary" @click="reload">搜索</a-button>
          <a-button @click="loadData">刷新</a-button>
        </a-space>
      </header>

      <AdminPageLoading v-if="loading && !records.length" />

      <AdminPageError v-else-if="pageError" :error="pageError" @retry="loadData" />

      <a-card v-else class="table-card" :bordered="false">
        <a-table
          row-key="id"
          size="middle"
          table-layout="fixed"
          :columns="columns"
          :data-source="records"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 'max-content' }"
          @change="handleTableChange"
        >
          <template #emptyText>
            <div class="empty-wrap">
              <AdminTableEmpty
                :filtered="hasKeywordFilter"
                empty-title="暂无应用"
                empty-description="平台尚未创建任何应用，或当前筛选无匹配结果"
                @reset="resetFilters"
              />
            </div>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <a-tooltip :title="record.name">
                <span class="cell-ellipsis">{{ record.name }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'appType'">
              {{ appTypeLabel(record.appType) }}
            </template>
            <template v-else-if="column.key === 'workspaceId'">
              <a-tooltip :title="String(record.workspaceId)">
                <span class="cell-ellipsis">{{ record.workspaceId }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'currentVersionId'">
              {{ formatVersion(record.currentVersionId) }}
            </template>
            <template v-else-if="column.key === 'generationStatus'">
              <a-tag :color="generationStatusColor(record)">
                {{ appGenerationStatusLabel(record.status, record.latestTaskId) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'updatedAt'">
              {{ formatTime(record.updatedAt) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space :size="4">
                <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
                <a-button type="link" size="small" @click="openApp(record.id)">打开</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-drawer v-model:open="detailOpen" title="应用详情" width="480" destroy-on-close>
        <a-descriptions v-if="detailRecord" :column="1" bordered size="small">
          <a-descriptions-item label="应用 ID">{{ detailRecord.id }}</a-descriptions-item>
          <a-descriptions-item label="应用名称">{{ detailRecord.name }}</a-descriptions-item>
          <a-descriptions-item label="应用类型">{{ appTypeLabel(detailRecord.appType) }}</a-descriptions-item>
          <a-descriptions-item label="所属工作空间">{{ detailRecord.workspaceId }}</a-descriptions-item>
          <a-descriptions-item label="当前版本">{{ formatVersion(detailRecord.currentVersionId) }}</a-descriptions-item>
          <a-descriptions-item label="最近任务">{{ detailRecord.latestTaskId ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="生成状态">
            {{ appGenerationStatusLabel(detailRecord.status, detailRecord.latestTaskId) }}
          </a-descriptions-item>
          <a-descriptions-item label="应用状态">{{ appStatusLabel(detailRecord.status) }}</a-descriptions-item>
          <a-descriptions-item label="可见性">{{ detailRecord.visibility ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ detailRecord.description || '—' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailRecord.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ formatTime(detailRecord.updatedAt) }}</a-descriptions-item>
        </a-descriptions>
      </a-drawer>
    </div>
  </AdminPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { listAdminApps, type AdminAppListItemResponse } from '@/api/admin'
import { AdminApiError } from '@/types/adminError'
import {
  AdminPageError,
  AdminPageLoading,
  AdminPageShell,
  AdminTableEmpty,
} from '@/components/admin'
import {
  appGenerationStatusLabel,
  appStatusLabel,
  appTypeLabel,
} from '@/utils/adminLabels'
import { formatTime } from '@/utils/time'

const router = useRouter()
const loading = ref(false)
const pageError = ref<AdminApiError | null>(null)
const keyword = ref('')
const records = ref<AdminAppListItemResponse[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const detailOpen = ref(false)
const detailRecord = ref<AdminAppListItemResponse | null>(null)

const hasKeywordFilter = computed(() => Boolean(keyword.value.trim()))

const columns: TableColumnsType<AdminAppListItemResponse> = [
  { title: '应用 ID', dataIndex: 'id', key: 'id', width: 108, ellipsis: true },
  { title: '应用名称', dataIndex: 'name', key: 'name', width: 160, ellipsis: true },
  { title: '应用类型', dataIndex: 'appType', key: 'appType', width: 100 },
  { title: '所属工作空间', dataIndex: 'workspaceId', key: 'workspaceId', width: 120, ellipsis: true },
  { title: '当前版本', dataIndex: 'currentVersionId', key: 'currentVersionId', width: 96 },
  { title: '生成状态', key: 'generationStatus', width: 96 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 168 },
  { title: '操作', key: 'action', width: 120, fixed: 'right' },
]

const pagination = computed<TablePaginationConfig>(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (value) => `共 ${value} 条`,
}))

const formatVersion = (versionId?: AdminAppListItemResponse['currentVersionId']) => {
  if (versionId == null) return '—'
  return `#${versionId}`
}

const generationStatusColor = (record: AdminAppListItemResponse) => {
  if (record.status === 'READY' || record.status === 'DEVELOPING') return 'green'
  if (record.status === 'DRAFT') return 'default'
  return 'orange'
}

const loadData = async () => {
  loading.value = true
  pageError.value = null
  try {
    const res = await listAdminApps({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    records.value = res.data.data?.records ?? []
    total.value = Number(res.data.data?.total ?? 0)
  } catch (error) {
    records.value = []
    total.value = 0
    pageError.value = error instanceof AdminApiError ? error : null
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  pageNo.value = 1
  await loadData()
}

const resetFilters = async () => {
  keyword.value = ''
  pageNo.value = 1
  await loadData()
}

const handleTableChange = async (pager: TablePaginationConfig) => {
  pageNo.value = pager.current || 1
  pageSize.value = pager.pageSize || 20
  await loadData()
}

const openDetail = (record: AdminAppListItemResponse) => {
  detailRecord.value = record
  detailOpen.value = true
}

const openApp = (appId: AdminAppListItemResponse['id']) => {
  router.push(`/apps/${appId}`)
}

onMounted(loadData)
</script>

<style scoped>
.app-manage-page {
  width: 100%;
  max-width: 100%;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.page-heading {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}
.page-subtitle {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}
.search-input {
  width: 240px;
}
.table-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.empty-wrap {
  padding: 40px 16px;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
@media (max-width: 768px) {
  .search-input {
    width: 100%;
  }
  .toolbar-actions {
    width: 100%;
  }
}
</style>
