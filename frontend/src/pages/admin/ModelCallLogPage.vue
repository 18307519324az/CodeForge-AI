<template>
  <AdminPageShell>
    <div class="log-page">
      <header class="page-header">
        <div>
          <h2 class="page-heading">模型调用日志</h2>
          <p class="page-subtitle">查看模型调用记录与执行详情</p>
        </div>
        <a-space wrap>
          <a-input
            v-model:value="keyword"
            allow-clear
            class="search-input"
            placeholder="搜索任务 ID、供应商或模型"
            @press-enter="reload"
          />
          <a-button type="primary" @click="reload">搜索</a-button>
          <a-button @click="reload">刷新</a-button>
        </a-space>
      </header>

      <AdminPageError v-if="pageError" :error="pageError" @retry="reload" />

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
                empty-title="暂无调用日志"
                empty-description="当前还没有模型调用记录"
                @reset="resetFilters"
              />
            </div>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'providerCode'">
              <a-tooltip :title="record.providerCode || '—'">
                <span class="cell-ellipsis">{{ record.providerCode || '—' }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'modelName'">
              <a-tooltip :title="record.modelName || '—'">
                <span class="cell-ellipsis">{{ record.modelName || '—' }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'generationSource'">
              <a-tag :color="sourceColor(record.generationSource)">
                {{ modelLogSourceLabel(record.generationSource) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">
                {{ modelLogStatusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'durationMs'">
              {{ formatDuration(record.durationMs) }}
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-drawer v-model:open="detailOpen" title="调用详情" width="520" destroy-on-close>
        <a-descriptions v-if="detailRecord" :column="1" bordered size="small">
          <a-descriptions-item label="调用 ID">{{ detailRecord.id }}</a-descriptions-item>
          <a-descriptions-item label="任务 ID">{{ detailRecord.taskId ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="应用 ID">{{ detailRecord.appId ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="供应商">{{ detailRecord.providerCode ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="模型">{{ detailRecord.modelName ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="来源">
            {{ modelLogSourceLabel(detailRecord.generationSource) }}
          </a-descriptions-item>
          <a-descriptions-item label="降级">
            <a-tag :color="detailRecord.fallbackUsed ? 'orange' : 'green'">
              {{ fallbackLabel(detailRecord.fallbackUsed) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            {{ modelLogStatusLabel(detailRecord.status) }}
          </a-descriptions-item>
          <a-descriptions-item label="输入 Token">{{ detailRecord.inputTokens ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="输出 Token">{{ detailRecord.outputTokens ?? 0 }}</a-descriptions-item>
          <a-descriptions-item label="耗时">{{ formatDuration(detailRecord.durationMs) }}</a-descriptions-item>
          <a-descriptions-item label="请求 ID">{{ detailRecord.requestId ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="错误信息">
            <span class="detail-error">{{ detailRecord.errorMessage || '—' }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailRecord.createdAt) }}</a-descriptions-item>
        </a-descriptions>
      </a-drawer>
    </div>
  </AdminPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { listModelCallLogs, type ModelCallLogResponse } from '@/api/admin'
import { AdminApiError } from '@/types/adminError'
import { AdminPageError, AdminPageShell, AdminTableEmpty } from '@/components/admin'
import {
  fallbackLabel,
  modelLogSourceLabel,
  modelLogStatusLabel,
} from '@/utils/adminLabels'
import { formatTime } from '@/utils/time'

const loading = ref(false)
const pageError = ref<AdminApiError | null>(null)
const keyword = ref('')
const records = ref<ModelCallLogResponse[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const detailOpen = ref(false)
const detailRecord = ref<ModelCallLogResponse | null>(null)

const hasKeywordFilter = computed(() => Boolean(keyword.value.trim()))

const columns: TableColumnsType<ModelCallLogResponse> = [
  { title: '调用 ID', dataIndex: 'id', key: 'id', width: 108, ellipsis: true },
  { title: '任务 ID', dataIndex: 'taskId', key: 'taskId', width: 108, ellipsis: true },
  { title: '应用', dataIndex: 'appId', key: 'appId', width: 96, ellipsis: true },
  { title: '供应商', dataIndex: 'providerCode', key: 'providerCode', width: 96, ellipsis: true },
  { title: '模型', dataIndex: 'modelName', key: 'modelName', width: 120, ellipsis: true },
  { title: '来源', dataIndex: 'generationSource', key: 'generationSource', width: 96 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '耗时', dataIndex: 'durationMs', key: 'durationMs', width: 88 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 168 },
  { title: '操作', key: 'action', width: 72, fixed: 'right' },
]

const pagination = computed<TablePaginationConfig>(() => ({
  current: pageNo.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (value) => `共 ${value} 条`,
}))

const sourceColor = (source?: string) => {
  if (source === 'AI_DIRECT') return 'green'
  if (source === 'RULE_FALLBACK') return 'orange'
  if (source === 'RULE_ONLY') return 'default'
  return 'default'
}

const statusColor = (status?: string) => {
  if (status === 'SUCCESS') return 'green'
  if (status === 'FAILED') return 'red'
  if (status === 'TIMEOUT') return 'volcano'
  if (status === 'CANCELED') return 'default'
  return 'processing'
}

const formatDuration = (value?: number) => {
  if (value == null) return '—'
  return `${value} ms`
}

const openDetail = (record: ModelCallLogResponse) => {
  detailRecord.value = record
  detailOpen.value = true
}

const loadData = async () => {
  loading.value = true
  pageError.value = null
  try {
    const response = await listModelCallLogs({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    records.value = response.data.data?.records || []
    total.value = Number(response.data.data?.total || 0)
  } catch (error) {
    records.value = []
    total.value = 0
    if (error instanceof AdminApiError && (error.httpStatus ?? 0) >= 500) {
      pageError.value = error
    }
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

onMounted(loadData)
</script>

<style scoped>
.log-page {
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
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 4px;
}
.page-subtitle {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}
.search-input {
  width: 260px;
}
.table-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.empty-wrap {
  padding: 48px 16px;
  display: flex;
  justify-content: center;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.detail-error {
  word-break: break-all;
  white-space: pre-wrap;
}
@media (max-width: 768px) {
  .search-input {
    width: 100%;
  }
}
</style>
