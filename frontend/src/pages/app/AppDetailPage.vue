<template>
  <div class="app-detail-hub">
    <a-spin :spinning="pageLoading">
      <template v-if="app">
        <header class="detail-header">
          <div class="detail-header-left">
            <a-button size="small" class="back-btn" @click="router.push('/apps')">
              <ArrowLeftOutlined />
              返回
            </a-button>
            <div class="detail-title-block">
              <a-tooltip :title="app.name">
                <h1 class="detail-title">{{ app.name }}</h1>
              </a-tooltip>
              <div class="detail-tags">
                <a-tag :color="appStatusColor(app.status)">{{ appStatusLabel(app.status) }}</a-tag>
                <a-tag color="blue">{{ appTypeLabel(app.appType) }}</a-tag>
              </div>
              <p class="detail-desc">{{ app.description || '暂无描述' }}</p>
            </div>
          </div>
          <div class="detail-header-actions">
            <a-button type="primary" @click="goWorkspace">
              <ThunderboltOutlined />
              进入 AI 工作台
            </a-button>
            <a-button @click="goVersions">
              <HistoryOutlined />
              查看版本
            </a-button>
            <a-button v-if="!isPublished" type="default" @click="publishOpen = true">
              <CloudUploadOutlined />
              发布
            </a-button>
            <template v-else>
              <a-button @click="openPublicPage">
                <GlobalOutlined />
                查看公开页
              </a-button>
              <a-button @click="publishOpen = true">
                <SettingOutlined />
                发布设置
              </a-button>
              <a-button danger :loading="unpublishing" @click="handleUnpublish">
                取消发布
              </a-button>
            </template>
            <a-dropdown>
              <a-button>
                更多
                <DownOutlined />
              </a-button>
              <template #overlay>
                <a-menu @click="handleMoreMenu">
                  <a-menu-item key="preview">
                    <EyeOutlined />
                    预览
                  </a-menu-item>
                  <a-menu-item key="export" :disabled="!canExport">
                    <ExportOutlined />
                    导出源码
                  </a-menu-item>
                  <a-menu-item key="edit">
                    <EditOutlined />
                    编辑
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </header>

        <div class="detail-grid">
          <div class="detail-main">
            <a-card title="应用信息" class="section-card" :bordered="false">
              <a-descriptions :column="{ xxl: 2, xl: 2, lg: 2, md: 2, sm: 1, xs: 1 }" size="small" class="info-descriptions">
                <a-descriptions-item label="应用类型">
                  {{ appTypeLabel(app.appType) }}
                </a-descriptions-item>
                <a-descriptions-item label="当前状态">
                  <a-tag :color="displayStatusColor(app.displayStatus)">
                    {{ displayStatusLabel(app.displayStatus) }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="发布状态">
                  <a-tag :color="publicationStatusColor(app.publicationStatus)">
                    {{ publicationStatusLabel(app.publicationStatus) }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="当前版本">
                  {{ formatVersionNoFromNumber(app.currentVersionNo) }}
                </a-descriptions-item>
                <a-descriptions-item label="工作空间">
                  <a-tooltip :title="String(app.workspaceId)">
                    <span class="cell-ellipsis">{{ workspaceLabel(app.workspaceId) }}</span>
                  </a-tooltip>
                </a-descriptions-item>
                <a-descriptions-item label="创建时间">
                  {{ formatTime(app.createdAt) }}
                </a-descriptions-item>
                <a-descriptions-item label="更新时间">
                  {{ formatTime(app.updatedAt) }}
                </a-descriptions-item>
              </a-descriptions>
            </a-card>

            <a-card title="最近生成记录" class="section-card" :bordered="false">
              <a-table
                v-if="enrichedRecords.length"
                row-key="id"
                size="small"
                table-layout="fixed"
                :columns="recordColumns"
                :data-source="enrichedRecords"
                :pagination="false"
                :scroll="{ x: 'max-content' }"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'taskStatus'">
                    <a-tag :color="taskStatusColor(record.taskStatus)">
                      {{ taskStatusLabel(record.taskStatus) }}
                    </a-tag>
                  </template>
                  <template v-else-if="column.key === 'source'">
                    {{ generationSourceLabel(record) }}
                  </template>
                  <template v-else-if="column.key === 'version'">
                    {{ formatVersionNo(record.promptTemplateVersionId) }}
                  </template>
                  <template v-else-if="column.key === 'durationMs'">
                    {{ formatDuration(record.durationMs) }}
                  </template>
                  <template v-else-if="column.key === 'createdAt'">
                    {{ formatTime(record.createdAt) }}
                  </template>
                  <template v-else-if="column.key === 'action'">
                    <a-button type="link" size="small" @click="goTaskDetail(record.taskId)">
                      查看详情
                    </a-button>
                  </template>
                </template>
              </a-table>
              <div v-else class="compact-empty">
                <a-empty description="暂无生成记录" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
              </div>
            </a-card>

            <a-card class="section-card export-card" :bordered="false">
              <template #title>
                <div class="export-card-title">
                  <span>导出记录</span>
                  <a-button v-if="exports.length > 3" type="link" size="small" @click="scrollToExports">
                    查看全部
                  </a-button>
                </div>
              </template>
              <div v-if="displayExports.length" class="export-list">
                <div v-for="item in displayExports" :key="item.id" class="export-row">
                  <div class="export-row-main">
                    <a-tag :color="exportStatusColor(item.status)">
                      {{ exportStatusLabel(item.status) }}
                    </a-tag>
                    <span class="export-type">{{ packageTypeLabel(item.packageType) }}</span>
                    <span class="export-time">{{ formatTime(item.createdAt) }}</span>
                  </div>
                  <div class="export-row-actions">
                    <a-button
                      v-if="item.status === 'READY'"
                      type="link"
                      size="small"
                      :loading="downloadingId === item.id"
                      @click="handleDownload(item.id)"
                    >
                      下载
                    </a-button>
                    <a-button
                      v-if="item.status === 'FAILED'"
                      type="link"
                      size="small"
                      :loading="exporting"
                      @click="handleRetryExport"
                    >
                      重试
                    </a-button>
                  </div>
                </div>
              </div>
              <div v-else class="compact-empty export-empty">
                <a-empty description="暂无导出记录" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
              </div>
            </a-card>
          </div>

          <aside class="detail-aside">
            <a-card title="快捷操作" class="section-card quick-card" :bordered="false">
              <a-button type="primary" block size="large" class="quick-primary" @click="goWorkspace">
                <ThunderboltOutlined />
                进入 AI 工作台
              </a-button>
              <div class="quick-secondary">
                <a-tooltip :title="versionDisabledTip">
                  <a-button class="quick-grid-btn" :disabled="!app.currentVersionId" @click="goVersions">
                    <HistoryOutlined />
                    查看版本历史
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="filesDisabledTip">
                  <a-button class="quick-grid-btn" :disabled="!app.currentVersionId" @click="goFiles">
                    <FolderOpenOutlined />
                    浏览生成文件
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="previewDisabledTip">
                  <a-button
                    class="quick-grid-btn"
                    :disabled="!app.currentVersionId"
                    :loading="openingPreview"
                    @click="handleOpenPreview"
                  >
                    <EyeOutlined />
                    打开预览
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="exportDisabledTip">
                  <a-button
                    class="quick-grid-btn"
                    :disabled="!canExport"
                    :loading="exporting"
                    @click="handleExport"
                  >
                    <ExportOutlined />
                    导出源码
                  </a-button>
                </a-tooltip>
              </div>
            </a-card>
          </aside>
        </div>
      </template>

      <a-result
        v-else-if="!pageLoading && loadError"
        :status="loadError.kind === 'forbidden' ? '403' : loadError.kind === 'not-found' ? '404' : 'error'"
        :title="loadError.title"
        :sub-title="loadError.message"
      >
        <template #extra>
          <a-space>
            <a-button v-if="loadError.kind === 'server' || loadError.kind === 'generic'" type="primary" @click="loadPage">
              重试
            </a-button>
            <a-button
              v-if="loadError.kind === 'forbidden' || loadError.kind === 'not-found'"
              type="primary"
              @click="router.push('/apps')"
            >
              返回我的应用
            </a-button>
            <a-button v-if="loadError.kind === 'unauthorized'" type="primary" @click="router.push('/user/login')">
              重新登录
            </a-button>
          </a-space>
        </template>
      </a-result>
    </a-spin>

    <a-modal
      v-model:open="editOpen"
      title="编辑应用"
      :confirm-loading="saving"
      destroy-on-close
      @ok="submitEdit"
    >
      <a-form layout="vertical">
        <a-form-item label="应用名称" required>
          <a-input v-model:value="editForm.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" :rows="4" />
        </a-form-item>
      </a-form>
    </a-modal>

    <PublishAppDialog
      v-model:open="publishOpen"
      :app="app"
      @success="onPublishSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Empty, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  DownOutlined,
  EditOutlined,
  ExportOutlined,
  EyeOutlined,
  FolderOpenOutlined,
  GlobalOutlined,
  HistoryOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { getAiApp, updateAiApp, type AiAppDetailResponse } from '@/api/app'
import {
  createExportPackage,
  downloadExportPackageFile,
  listExportPackages,
  type ExportPackageListItemResponse,
} from '@/api/export'
import {
  getGenerationTask,
  listGenerationRecords,
  type GenerationRecordResponse,
} from '@/api/task'
import {
  appStatusColor,
  appStatusLabel,
  appTypeLabel,
  displayStatusColor,
  displayStatusLabel,
  exportStatusColor,
  exportStatusLabel,
  formatVersionNo,
  formatVersionNoFromNumber,
  generationSourceLabel,
  packageTypeLabel,
  taskStatusColor,
  taskStatusLabel,
  workspaceLabel,
} from '@/utils/appLabels'
import { formatTime } from '@/utils/time'
import { openAppVersionPreview } from '@/utils/openAppVersionPreview'
import PublishAppDialog from '@/components/PublishAppDialog.vue'
import { unpublishApp, type AppPublicationResponse } from '@/api/publication'
import { resolveApiAccessError, type ApiAccessErrorView } from '@/utils/apiAccessError'
import { clearAccessToken } from '@/auth/token'
import type { LongId } from '@/types/id'
import { buildGenerationDetailRoute } from '@/pages/app/generationDetailNavigation'

type EnrichedRecord = GenerationRecordResponse & {
  taskStatus?: string
  taskType?: string
}

const EXPORT_PACKAGE_TYPE = 'SOURCE_ZIP'
const route = useRoute()
const router = useRouter()
const appId = String(route.params.appId ?? '')

const pageLoading = ref(true)
const loadError = ref<ApiAccessErrorView | null>(null)
const app = ref<AiAppDetailResponse | null>(null)
const enrichedRecords = ref<EnrichedRecord[]>([])
const exports = ref<ExportPackageListItemResponse[]>([])
const exporting = ref(false)
const openingPreview = ref(false)
const downloadingId = ref<LongId>()
const editOpen = ref(false)
const publishOpen = ref(false)
const unpublishing = ref(false)
const saving = ref(false)
const editForm = reactive({ name: '', description: '' })

const canExport = computed(() => Boolean(app.value?.currentVersionId))
const isPublished = computed(() => app.value?.publicationStatus === 'PUBLISHED')
const displayExports = computed(() => exports.value.slice(0, 5))

const versionDisabledTip = computed(() =>
  app.value?.currentVersionId ? '' : '当前尚无版本，请先生成',
)
const filesDisabledTip = versionDisabledTip
const previewDisabledTip = computed(() =>
  app.value?.currentVersionId ? '' : '当前应用暂无可预览版本',
)
const exportDisabledTip = computed(() =>
  canExport.value ? '' : '当前没有可导出的版本',
)

const recordColumns: TableColumnsType<EnrichedRecord> = [
  { title: '任务状态', key: 'taskStatus', width: 96 },
  { title: '生成来源', key: 'source', width: 96 },
  { title: '任务 ID', dataIndex: 'taskId', key: 'taskId', width: 108, ellipsis: true },
  { title: '版本', key: 'version', width: 72 },
  { title: '创建时间', key: 'createdAt', width: 168 },
  { title: '耗时', key: 'durationMs', width: 88 },
  { title: '操作', key: 'action', width: 88, fixed: 'right' },
]

const formatDuration = (value?: number) => {
  if (value == null) return '—'
  return `${value} ms`
}

const publicationStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    PUBLISHED: '已发布',
    UNPUBLISHED: '已取消发布',
    NONE: '未发布',
  }
  return map[status ?? ''] ?? status ?? '未发布'
}

const publicationStatusColor = (status?: string) => {
  const map: Record<string, string> = {
    PUBLISHED: 'green',
    UNPUBLISHED: 'default',
    NONE: 'default',
  }
  return map[status ?? ''] ?? 'default'
}

const openPublicPage = () => {
  if (!app.value?.publicationSlug) {
    message.warning('公开页链接不可用')
    return
  }
  router.push(`/explore/apps/${app.value.publicationSlug}`)
}

const onPublishSuccess = async (publication: AppPublicationResponse) => {
  if (!app.value) return
  app.value = {
    ...app.value,
    publicationStatus: publication.status,
    publicationSlug: publication.slug,
    publicationId: publication.publicationId,
    displayStatus: publication.status === 'PUBLISHED' ? 'PUBLISHED' : app.value.displayStatus,
  }
  await loadPage()
}

const handleUnpublish = async () => {
  if (!app.value?.publicationId) {
    message.warning('当前应用未发布')
    return
  }
  unpublishing.value = true
  try {
    const response = await unpublishApp(appId, app.value.publicationId)
    if (response.data.code !== 0) {
      message.error(response.data.message || '取消发布失败')
      return
    }
    message.success('已取消发布')
    await loadPage()
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || '取消发布失败')
  } finally {
    unpublishing.value = false
  }
}

const enrichRecords = async (records: GenerationRecordResponse[]) => {
  const slice = records.slice(0, 5)
  return Promise.all(
    slice.map(async (record) => {
      try {
        const res = await getGenerationTask(record.taskId)
        if (res.data.code === 0 && res.data.data) {
          return {
            ...record,
            taskStatus: res.data.data.taskStatus,
            taskType: res.data.data.taskType,
          }
        }
      } catch {
        /* ignore per-record failure */
      }
      return {
        ...record,
        taskStatus: record.durationMs != null ? 'SUCCESS' : undefined,
        taskType: undefined,
      }
    }),
  )
}

const loadPage = async () => {
  pageLoading.value = true
  loadError.value = null
  try {
    const [appRes, recRes, expRes] = await Promise.all([
      getAiApp(appId),
      listGenerationRecords(appId),
      listExportPackages(appId),
    ])
    if (appRes.data.code === 0 && appRes.data.data) {
      app.value = appRes.data.data
    } else {
      loadError.value = resolveApiAccessError({
        response: {
          status: appRes.data.code === 40403 ? 404 : undefined,
          data: appRes.data,
        },
      })
      return
    }
    const records = recRes.data.code === 0 ? recRes.data.data ?? [] : []
    enrichedRecords.value = await enrichRecords(records)
    exports.value = expRes.data.code === 0 ? expRes.data.data ?? [] : []
  } catch (error: unknown) {
    const resolved = resolveApiAccessError(error)
    loadError.value = resolved
    if (resolved.clearToken) {
      clearAccessToken()
    }
    if (resolved.redirectToLogin) {
      await router.push('/user/login')
      return
    }
    if (resolved.kind !== 'forbidden' && resolved.kind !== 'not-found') {
      message.error(resolved.message)
    }
  } finally {
    pageLoading.value = false
  }
}

const goWorkspace = () => router.push(`/apps/${appId}/generate`)
const goVersions = () => router.push(`/apps/${appId}/versions`)
const goFiles = () => router.push(`/apps/${appId}/files`)

const handleOpenPreview = async () => {
  if (!app.value?.currentVersionId) {
    message.warning('当前应用暂无可预览版本')
    return
  }
  openingPreview.value = true
  try {
    await openAppVersionPreview(appId, app.value.currentVersionId)
  } finally {
    openingPreview.value = false
  }
}

const goTaskDetail = (taskId: LongId) => router.push(buildGenerationDetailRoute(appId, taskId))

const handleMoreMenu = ({ key }: { key: string | number }) => {
  const action = String(key)
  if (action === 'preview') void handleOpenPreview()
  if (action === 'export') void handleExport()
  if (action === 'edit') openEdit()
}

const openEdit = () => {
  if (!app.value) return
  editForm.name = app.value.name
  editForm.description = app.value.description ?? ''
  editOpen.value = true
}

const submitEdit = async () => {
  if (!app.value || !editForm.name.trim()) {
    message.warning('请输入应用名称')
    return
  }
  saving.value = true
  try {
    const res = await updateAiApp(appId, {
      name: editForm.name.trim(),
      description: editForm.description.trim() || undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      app.value = res.data.data
      editOpen.value = false
      message.success('应用已更新')
    }
  } catch {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleExport = async () => {
  if (!app.value?.currentVersionId) {
    message.warning('当前没有可导出的版本')
    return
  }
  exporting.value = true
  try {
    const res = await createExportPackage({
      appId: app.value.id,
      appVersionId: app.value.currentVersionId,
      packageType: EXPORT_PACKAGE_TYPE,
    })
    if (res.data.code === 0) {
      message.success('导出任务已创建')
      const expRes = await listExportPackages(appId)
      if (expRes.data.code === 0) {
        exports.value = expRes.data.data ?? []
      }
    }
  } catch {
    message.error('创建导出失败')
  } finally {
    exporting.value = false
  }
}

const handleRetryExport = () => void handleExport()

const handleDownload = async (packageId: LongId) => {
  downloadingId.value = packageId
  try {
    await downloadExportPackageFile(packageId)
    message.success('开始下载')
  } catch {
    message.error('下载失败')
  } finally {
    downloadingId.value = undefined
  }
}

const scrollToExports = () => {
  document.querySelector('.export-card')?.scrollIntoView({ behavior: 'smooth' })
}

onMounted(loadPage)
</script>

<style scoped>
.app-detail-hub {
  box-sizing: border-box;
  width: 100%;
  min-height: 100%;
  padding: 24px;
}
.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.detail-header-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
  flex: 1;
}
.back-btn {
  flex-shrink: 0;
  margin-top: 4px;
}
.detail-title-block {
  min-width: 0;
  flex: 1;
}
.detail-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.detail-desc {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.5;
}
.detail-header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 380px);
  gap: 24px;
  align-items: start;
}
.section-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  margin-bottom: 20px;
}
.detail-aside .section-card {
  margin-bottom: 0;
  position: sticky;
  top: 16px;
}
.info-descriptions :deep(.ant-descriptions-item-label) {
  width: 96px;
  color: #6b7280;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.compact-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.export-empty {
  min-height: 180px;
  max-height: 220px;
}
.export-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.export-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.export-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.export-row:last-child {
  border-bottom: none;
}
.export-row-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.export-type {
  color: #374151;
  font-size: 13px;
}
.export-time {
  color: #9ca3af;
  font-size: 12px;
  margin-left: auto;
}
.quick-primary {
  margin-bottom: 16px;
  height: 46px;
}
.quick-secondary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.quick-secondary :deep(.ant-tooltip-open),
.quick-secondary :deep(.ant-btn) {
  width: 100%;
}
.quick-grid-btn {
  height: 40px;
  min-width: 0;
  white-space: nowrap;
  padding-inline: 10px;
}
@media (max-width: 360px) {
  .quick-secondary {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 1200px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .detail-aside .section-card {
    position: static;
  }
}
@media (max-width: 768px) {
  .app-detail-hub {
    padding: 16px;
  }
  .detail-header-actions {
    width: 100%;
  }
  .detail-title {
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}
</style>
