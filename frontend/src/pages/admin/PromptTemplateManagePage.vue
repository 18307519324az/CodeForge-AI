<template>
  <AdminPageShell>
    <div class="prompt-admin-page">
      <header class="page-header">
        <div>
          <h2 class="page-heading">提示词模板管理</h2>
          <p class="page-subtitle">管理模板元数据、版本内容与发布状态</p>
        </div>
        <a-space wrap>
          <a-input
            v-model:value="keyword"
            allow-clear
            class="search-input"
            placeholder="搜索模板名称"
            @press-enter="loadTemplates"
          />
          <a-button @click="loadTemplates">刷新</a-button>
          <a-button type="primary" @click="openCreateTemplate">新增模板</a-button>
        </a-space>
      </header>

      <AdminPageLoading v-if="loading && !templates.length" />
      <AdminPageError v-else-if="pageError" :error="pageError" @retry="loadTemplates" />

      <a-card v-else :bordered="false">
        <a-table
          row-key="id"
          size="middle"
          :columns="columns"
          :data-source="templates"
          :loading="loading"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'templateScene'">
              {{ labelTemplateScene(record.templateScene) }}
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.key === 'versionCount'">
              {{ record.versionCount ?? '-' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space size="small" wrap>
                <a-button type="link" size="small" @click="openViewTemplate(record)">查看</a-button>
                <a-button type="link" size="small" @click="openEditTemplate(record)">编辑</a-button>
                <a-button type="link" size="small" @click="openManage(record)">版本</a-button>
                <a-dropdown>
                  <a-button type="link" size="small">更多</a-button>
                  <template #overlay>
                    <a-menu @click="onMoreMenuClick($event, record)">
                      <a-menu-item v-if="record.status === 'PUBLISHED'" key="archive">归档</a-menu-item>
                      <a-menu-item v-if="record.status === 'DRAFT'" key="delete">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <a-modal
      v-model:open="templateModalOpen"
      :title="templateModalMode === 'create' ? '新增模板' : '编辑模板'"
      :confirm-loading="templateSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitTemplateForm"
    >
      <a-form layout="vertical">
        <a-form-item v-if="templateModalMode === 'create'" label="工作空间" required>
          <a-select
            v-model:value="templateForm.workspaceId"
            :options="workspaceOptions"
            placeholder="选择工作空间"
          />
        </a-form-item>
        <a-form-item label="模板名称" required>
          <a-input v-model:value="templateForm.templateName" placeholder="例如：Vue 项目生成" />
        </a-form-item>
        <a-form-item label="场景" required>
          <a-select v-model:value="templateForm.templateScene" :options="sceneOptions" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="templateForm.remark" :rows="2" placeholder="模板简介" />
        </a-form-item>
        <template v-if="templateModalMode === 'create'">
          <a-form-item label="系统提示词" required>
            <a-textarea v-model:value="templateForm.systemPrompt" :rows="3" />
          </a-form-item>
          <a-form-item label="用户提示词" required>
            <a-textarea v-model:value="templateForm.userPrompt" :rows="3" />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="viewModalOpen"
      title="模板详情"
      :footer="null"
      width="640"
    >
      <a-descriptions v-if="viewTemplate" :column="1" bordered size="small">
        <a-descriptions-item label="模板名称">{{ viewTemplate.templateName }}</a-descriptions-item>
        <a-descriptions-item label="场景">{{ labelTemplateScene(viewTemplate.templateScene) }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ viewTemplate.status }}</a-descriptions-item>
        <a-descriptions-item label="当前版本">v{{ viewTemplate.currentVersionNo }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ viewTemplate.remark || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <a-drawer
      :open="drawerOpen"
      :title="selectedTemplate ? `版本管理：${selectedTemplate.templateName}` : '版本管理'"
      width="760"
      @close="drawerOpen = false"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="selectedTemplate">
          <a-space style="margin-bottom: 12px">
            <a-button type="primary" @click="openCreateVersion">新增版本</a-button>
          </a-space>
          <a-table
            row-key="id"
            size="small"
            :columns="versionColumns"
            :data-source="versions"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'versionStatus'">
                <a-space size="small">
                  <a-tag :color="isPublishedVersion(record) ? 'green' : 'default'">
                    {{ isPublishedVersion(record) ? '已发布' : '草稿' }}
                  </a-tag>
                  <a-tag
                    v-if="selectedTemplate && record.versionNo === selectedTemplate.currentVersionNo && isPublishedVersion(record)"
                    color="blue"
                  >
                    最新
                  </a-tag>
                </a-space>
              </template>
              <template v-else-if="column.key === 'publishedAt'">
                {{ record.publishedAt || '未发布' }}
              </template>
              <template v-else-if="column.key === 'preview'">
                {{ truncateText(record.userPrompt) }}
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space size="small" wrap>
                  <a-button type="link" size="small" @click="openViewVersion(record)">查看</a-button>
                  <a-button
                    type="link"
                    size="small"
                    :disabled="isPublishedVersion(record)"
                    @click="openEditVersion(record)"
                  >
                    编辑
                  </a-button>
                  <a-button
                    type="link"
                    size="small"
                    :disabled="isPublishedVersion(record)"
                    @click="confirmPublish(record)"
                  >
                    发布
                  </a-button>
                  <a-button
                    type="link"
                    size="small"
                    danger
                    :disabled="isPublishedVersion(record)"
                    @click="confirmDeleteVersion(record)"
                  >
                    删除
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </template>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="versionModalOpen"
      :title="versionModalMode === 'create' ? '新增版本' : versionModalMode === 'edit' ? '编辑版本' : '查看版本'"
      :confirm-loading="versionSaving"
      :ok-text="versionModalMode === 'view' ? undefined : '保存'"
      :cancel-text="versionModalMode === 'view' ? '关闭' : '取消'"
      :footer="versionModalMode === 'view' ? undefined : undefined"
      width="720"
      @ok="submitVersionForm"
    >
      <a-form layout="vertical">
        <a-form-item label="系统提示词" required>
          <a-textarea
            v-model:value="versionForm.systemPrompt"
            :rows="4"
            :disabled="versionModalMode === 'view'"
          />
        </a-form-item>
        <a-form-item label="用户提示词" required>
          <a-textarea
            v-model:value="versionForm.userPrompt"
            :rows="4"
            :disabled="versionModalMode === 'view'"
          />
        </a-form-item>
        <a-form-item label="变量 JSON">
          <a-textarea
            v-model:value="versionForm.variablesJson"
            :rows="4"
            :disabled="versionModalMode === 'view'"
            placeholder='{"app_name":{"type":"string","required":true,"description":"应用名称"}}'
          />
        </a-form-item>
      </a-form>
      <template v-if="versionModalMode === 'view'" #footer>
        <a-button @click="versionModalOpen = false">关闭</a-button>
      </template>
    </a-modal>
  </AdminPageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Modal, message } from 'ant-design-vue'
import {
  archiveAdminPromptTemplate,
  createAdminPromptTemplate,
  createAdminPromptTemplateVersion,
  deleteAdminPromptTemplate,
  deleteAdminPromptTemplateVersion,
  getAdminPromptTemplate,
  listAdminPromptTemplateVersions,
  listAdminPromptTemplates,
  loadDefaultWorkspace,
  publishAdminPromptTemplateVersion,
  updateAdminPromptTemplate,
  updateAdminPromptTemplateVersion,
  type PromptTemplateAdminDetail,
  type PromptTemplateAdminListItem,
  type PromptTemplateAdminVersion,
} from '@/api/promptTemplateAdmin'
import { AdminPageError, AdminPageLoading, AdminPageShell } from '@/components/admin'
import { labelTemplateScene } from '@/utils/promptTemplateScene'
import { AdminApiError } from '@/types/adminError'

const sceneOptions = [
  { label: '代码生成', value: 'CODE_GEN' },
  { label: 'API 接口', value: 'API_GEN' },
  { label: '页面生成', value: 'PAGE_GENERATION' },
  { label: '应用生成', value: 'APP_GENERATION' },
  { label: '后台管理', value: 'ADMIN_WEB' },
  { label: '内容创作', value: 'CONTENT' },
  { label: '效率工具', value: 'EFFICIENCY' },
  { label: '数据看板', value: 'DASHBOARD' },
  { label: '电商', value: 'ECOMMERCE' },
]

const keyword = ref('')
const loading = ref(false)
const pageError = ref<AdminApiError | null>(null)
const templates = ref<PromptTemplateAdminListItem[]>([])
const workspaceOptions = ref<{ label: string; value: number }[]>([])

const templateModalOpen = ref(false)
const templateModalMode = ref<'create' | 'edit'>('create')
const templateSaving = ref(false)
const editingTemplateId = ref<number | null>(null)
const templateForm = ref({
  workspaceId: undefined as number | undefined,
  templateName: '',
  templateScene: 'CODE_GEN',
  remark: '',
  systemPrompt: '',
  userPrompt: '',
})

const viewModalOpen = ref(false)
const viewTemplate = ref<PromptTemplateAdminDetail | null>(null)

const drawerOpen = ref(false)
const detailLoading = ref(false)
const selectedTemplate = ref<PromptTemplateAdminDetail | null>(null)
const versions = ref<PromptTemplateAdminVersion[]>([])

const versionModalOpen = ref(false)
const versionModalMode = ref<'create' | 'edit' | 'view'>('create')
const versionSaving = ref(false)
const editingVersionNo = ref<number | null>(null)
const versionForm = ref({
  systemPrompt: '',
  userPrompt: '',
  variablesJson: '',
})

const columns = [
  { title: '模板名称', dataIndex: 'templateName', key: 'templateName' },
  { title: '场景', dataIndex: 'templateScene', key: 'templateScene', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '当前版本', dataIndex: 'currentVersionNo', key: 'currentVersionNo', width: 90 },
  { title: '版本数', key: 'versionCount', width: 80 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'action', width: 260 },
]

const versionColumns = [
  { title: '版本号', dataIndex: 'versionNo', key: 'versionNo', width: 80 },
  { title: '状态', key: 'versionStatus', width: 120 },
  { title: '用户提示词', key: 'preview' },
  { title: '发布时间', key: 'publishedAt', width: 170 },
  { title: '操作', key: 'action', width: 220 },
]

const isPublishedVersion = (version: PromptTemplateAdminVersion) =>
  version.versionStatus === 'PUBLISHED' || Boolean(version.publishedAt)

const statusColor = (status: string) => {
  if (status === 'PUBLISHED') return 'green'
  if (status === 'ARCHIVED') return 'orange'
  return 'default'
}

const truncateText = (value?: string) => {
  if (!value) return '-'
  return value.length > 80 ? `${value.slice(0, 80)}...` : value
}

const loadWorkspaces = async () => {
  const workspace = await loadDefaultWorkspace()
  if (!workspace) return
  workspaceOptions.value = [{ label: workspace.name, value: Number(workspace.id) }]
  if (!templateForm.value.workspaceId) {
    templateForm.value.workspaceId = Number(workspace.id)
  }
}

const loadTemplates = async () => {
  loading.value = true
  pageError.value = null
  try {
    const response = await listAdminPromptTemplates({
      keyword: keyword.value || undefined,
      pageNo: 1,
      pageSize: 100,
    })
    if (response.data.code !== 0) {
      throw new Error(response.data.message || '加载模板列表失败')
    }
    templates.value = response.data.data?.records || []
  } catch (error: unknown) {
    pageError.value = error instanceof AdminApiError ? error : null
    templates.value = []
  } finally {
    loading.value = false
  }
}

const openCreateTemplate = async () => {
  await loadWorkspaces()
  templateModalMode.value = 'create'
  editingTemplateId.value = null
  templateForm.value = {
    workspaceId: workspaceOptions.value[0]?.value,
    templateName: '',
    templateScene: 'CODE_GEN',
    remark: '',
    systemPrompt: '你是专业的代码生成助手。',
    userPrompt: '请生成一个 {{app_name}} 项目',
  }
  templateModalOpen.value = true
}

const openEditTemplate = async (record: PromptTemplateAdminListItem) => {
  const response = await getAdminPromptTemplate(record.id)
  if (response.data.code !== 0 || !response.data.data) {
    message.error(response.data.message || '加载模板失败')
    return
  }
  templateModalMode.value = 'edit'
  editingTemplateId.value = record.id
  templateForm.value = {
    workspaceId: response.data.data.workspaceId,
    templateName: response.data.data.templateName,
    templateScene: response.data.data.templateScene,
    remark: response.data.data.remark || '',
    systemPrompt: '',
    userPrompt: '',
  }
  templateModalOpen.value = true
}

const submitTemplateForm = async () => {
  if (!templateForm.value.templateName.trim() || !templateForm.value.templateScene) {
    message.warning('请填写模板名称和场景')
    return Promise.reject()
  }
  templateSaving.value = true
  try {
    if (templateModalMode.value === 'create') {
      if (!templateForm.value.workspaceId || !templateForm.value.systemPrompt || !templateForm.value.userPrompt) {
        message.warning('请完整填写创建信息')
        return Promise.reject()
      }
      const response = await createAdminPromptTemplate({
        workspaceId: templateForm.value.workspaceId,
        templateName: templateForm.value.templateName.trim(),
        templateScene: templateForm.value.templateScene,
        remark: templateForm.value.remark,
        systemPrompt: templateForm.value.systemPrompt,
        userPrompt: templateForm.value.userPrompt,
      })
      if (response.data.code !== 0) throw new Error(response.data.message || '创建失败')
      message.success('模板创建成功')
    } else if (editingTemplateId.value) {
      const response = await updateAdminPromptTemplate(editingTemplateId.value, {
        templateName: templateForm.value.templateName.trim(),
        templateScene: templateForm.value.templateScene,
        remark: templateForm.value.remark,
      })
      if (response.data.code !== 0) throw new Error(response.data.message || '更新失败')
      message.success('模板更新成功')
    }
    templateModalOpen.value = false
    await loadTemplates()
  } catch (error: unknown) {
    message.error(String((error as Error)?.message || '保存失败'))
    return Promise.reject()
  } finally {
    templateSaving.value = false
  }
}

const openViewTemplate = async (record: PromptTemplateAdminListItem) => {
  const response = await getAdminPromptTemplate(record.id)
  if (response.data.code !== 0 || !response.data.data) {
    message.error(response.data.message || '加载模板失败')
    return
  }
  viewTemplate.value = response.data.data
  viewModalOpen.value = true
}

const openManage = async (record: PromptTemplateAdminListItem) => {
  drawerOpen.value = true
  detailLoading.value = true
  selectedTemplate.value = null
  versions.value = []
  try {
    const [detailRes, versionsRes] = await Promise.all([
      getAdminPromptTemplate(record.id),
      listAdminPromptTemplateVersions(record.id),
    ])
    if (detailRes.data.code !== 0) throw new Error(detailRes.data.message || '加载模板详情失败')
    if (versionsRes.data.code !== 0) throw new Error(versionsRes.data.message || '加载版本列表失败')
    selectedTemplate.value = detailRes.data.data
    versions.value = versionsRes.data.data || []
  } catch (error: unknown) {
    message.error(String((error as Error)?.message || '加载模板详情失败'))
    drawerOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

const onMoreMenuClick = (event: { key: string }, record: PromptTemplateAdminListItem) => {
  handleMoreAction(event, record)
}

const handleMoreAction = (event: { key: string }, record: PromptTemplateAdminListItem) => {
  if (event.key === 'archive') {
    Modal.confirm({
      title: '确认归档模板',
      content: `归档后用户侧将不再展示「${record.templateName}」。`,
      okText: '确认归档',
      onOk: async () => {
        const response = await archiveAdminPromptTemplate(record.id)
        if (response.data.code !== 0) throw new Error(response.data.message || '归档失败')
        message.success('模板已归档')
        await loadTemplates()
      },
    })
    return
  }
  if (event.key === 'delete') {
    Modal.confirm({
      title: '确认删除模板',
      content: `将删除草稿模板「${record.templateName}」，此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      onOk: async () => {
        const response = await deleteAdminPromptTemplate(record.id)
        if (response.data.code !== 0) throw new Error(response.data.message || '删除失败')
        message.success('模板已删除')
        await loadTemplates()
      },
    })
  }
}

const openCreateVersion = () => {
  versionModalMode.value = 'create'
  editingVersionNo.value = null
  versionForm.value = {
    systemPrompt: '你是专业的代码生成助手。',
    userPrompt: '请生成一个 {{app_name}} 项目',
    variablesJson: '{"app_name":{"type":"string","required":true,"description":"应用名称"}}',
  }
  versionModalOpen.value = true
}

const openEditVersion = (version: PromptTemplateAdminVersion) => {
  versionModalMode.value = 'edit'
  editingVersionNo.value = version.versionNo
  versionForm.value = {
    systemPrompt: version.systemPrompt,
    userPrompt: version.userPrompt,
    variablesJson: version.variablesJson || '',
  }
  versionModalOpen.value = true
}

const openViewVersion = (version: PromptTemplateAdminVersion) => {
  versionModalMode.value = 'view'
  versionForm.value = {
    systemPrompt: version.systemPrompt,
    userPrompt: version.userPrompt,
    variablesJson: version.variablesJson || '',
  }
  versionModalOpen.value = true
}

const submitVersionForm = async () => {
  if (!selectedTemplate.value) return Promise.reject()
  if (!versionForm.value.systemPrompt || !versionForm.value.userPrompt) {
    message.warning('请填写提示词内容')
    return Promise.reject()
  }
  versionSaving.value = true
  try {
    const payload = {
      systemPrompt: versionForm.value.systemPrompt,
      userPrompt: versionForm.value.userPrompt,
      variablesJson: versionForm.value.variablesJson || undefined,
    }
    if (versionModalMode.value === 'create') {
      const response = await createAdminPromptTemplateVersion(selectedTemplate.value.id, payload)
      if (response.data.code !== 0) throw new Error(response.data.message || '新增版本失败')
      message.success('版本创建成功')
    } else if (versionModalMode.value === 'edit' && editingVersionNo.value) {
      const response = await updateAdminPromptTemplateVersion(
        selectedTemplate.value.id,
        editingVersionNo.value,
        payload,
      )
      if (response.data.code !== 0) throw new Error(response.data.message || '更新版本失败')
      message.success('版本更新成功')
    }
    versionModalOpen.value = false
    await openManage({ id: selectedTemplate.value.id } as PromptTemplateAdminListItem)
    await loadTemplates()
  } catch (error: unknown) {
    message.error(String((error as Error)?.message || '保存版本失败'))
    return Promise.reject()
  } finally {
    versionSaving.value = false
  }
}

const confirmPublish = (version: PromptTemplateAdminVersion) => {
  if (!selectedTemplate.value) return
  Modal.confirm({
    title: '确认发布模板版本',
    content: `模板「${selectedTemplate.value.templateName}」将发布 v${version.versionNo} 到用户侧模板库。`,
    okText: '确认发布',
    cancelText: '取消',
    onOk: async () => {
      const response = await publishAdminPromptTemplateVersion(selectedTemplate.value!.id, version.versionNo)
      if (response.data.code !== 0) throw new Error(response.data.message || '发布失败')
      message.success('模板版本发布成功')
      selectedTemplate.value = response.data.data
      const versionsRes = await listAdminPromptTemplateVersions(selectedTemplate.value.id)
      if (versionsRes.data.code === 0) versions.value = versionsRes.data.data || []
      await loadTemplates()
    },
  })
}

const confirmDeleteVersion = (version: PromptTemplateAdminVersion) => {
  if (!selectedTemplate.value) return
  Modal.confirm({
    title: '确认删除版本',
    content: `将删除 v${version.versionNo} 草稿版本。`,
    okText: '确认删除',
    okType: 'danger',
    onOk: async () => {
      const response = await deleteAdminPromptTemplateVersion(selectedTemplate.value!.id, version.versionNo)
      if (response.data.code !== 0) throw new Error(response.data.message || '删除版本失败')
      message.success('版本已删除')
      await openManage({ id: selectedTemplate.value!.id } as PromptTemplateAdminListItem)
      await loadTemplates()
    },
  })
}

onMounted(() => {
  void loadWorkspaces()
  void loadTemplates()
})
</script>

<style scoped>
.prompt-admin-page { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.page-heading { margin: 0; font-size: 20px; }
.page-subtitle { margin: 4px 0 0; color: var(--cf-text-secondary); }
.search-input { width: 240px; }
</style>
