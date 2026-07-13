<template>
  <div class="version-page">
    <div class="page-toolbar">
      <a-space>
        <a-button size="small" @click="router.push(`/apps/${appId}`)">← 返回详情</a-button>
        <h2 class="page-heading">版本快照</h2>
      </a-space>
    </div>
    <a-spin :spinning="loading">
      <a-card v-if="versions.length" class="info-card" :bordered="false">
        <a-table
          row-key="id"
          size="middle"
          table-layout="fixed"
          :columns="columns"
          :data-source="versions"
          :pagination="false"
          :scroll="{ x: 'max-content' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'versionNo'">
              v{{ record.versionNo }}
            </template>
            <template v-else-if="column.key === 'changeSummary'">
              <a-tooltip :title="formatVersionChangeSummary(record.changeSummary, 200)">
                <span class="summary-cell">
                  {{ formatVersionChangeSummary(record.changeSummary) }}
                </span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="versionStatusColor(record.status)">
                {{ versionStatusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'publishedAt'">
              {{ formatTime(record.publishedAt || record.createdAt) }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space :size="4">
                <a-button type="link" size="small" @click="goFiles(record.id)">查看文件</a-button>
                <a-button
                  type="link"
                  size="small"
                  :loading="previewingId === record.id"
                  @click="handlePreview(record.id)"
                >
                  打开预览
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>
      <a-empty v-else description="暂无版本记录" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { listAppVersions, type AppVersionListItemResponse } from '@/api/appVersion'
import {
  formatVersionChangeSummary,
  versionStatusColor,
  versionStatusLabel,
} from '@/utils/appLabels'
import { openStaticPreview } from '@/utils/openStaticPreview'
import { formatTime } from '@/utils/time'
import type { LongId } from '@/types/id'

const route = useRoute()
const router = useRouter()
const appId = String(route.params.appId ?? '')
const loading = ref(false)
const previewingId = ref<LongId>()
const versions = ref<AppVersionListItemResponse[]>([])

const columns: TableColumnsType<AppVersionListItemResponse> = [
  { title: '版本', key: 'versionNo', dataIndex: 'versionNo', width: 100 },
  { title: '变更说明', key: 'changeSummary', width: 360, ellipsis: true },
  { title: '状态', key: 'status', dataIndex: 'status', width: 100 },
  { title: '发布时间', key: 'publishedAt', width: 180 },
  { title: '操作', key: 'actions', width: 140, fixed: 'right' },
]

const goFiles = (versionId: LongId) => {
  router.push(`/apps/${appId}/files?versionId=${versionId}`)
}

const handlePreview = async (versionId: LongId) => {
  previewingId.value = versionId
  try {
    await openStaticPreview(appId, versionId)
  } finally {
    previewingId.value = undefined
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await listAppVersions(appId, { pageNo: 1, pageSize: 50 })
    if (res.data.code === 0 && res.data.data) {
      versions.value = res.data.data.records || []
      return
    }
    message.error(res.data.message || '加载版本失败')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载版本失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.version-page {
  width: 100%;
  max-width: 100%;
  padding: 24px;
  box-sizing: border-box;
}
.page-toolbar {
  margin-bottom: 16px;
}
.page-heading {
  margin: 0;
  font-size: 20px;
  display: inline;
}
.info-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.summary-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.version-page :deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
}
@media (max-width: 768px) {
  .version-page {
    padding: 16px;
  }
}
</style>
