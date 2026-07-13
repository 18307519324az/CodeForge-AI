<template>
  <AdminPageShell>
    <div class="provider-page">
      <header class="page-header">
        <div>
          <h2 class="page-heading">模型供应商</h2>
          <p class="page-subtitle">管理 AI 模型供应商、路由策略与凭据配置</p>
        </div>
        <a-space wrap class="toolbar-actions">
          <a-input
            v-model:value="keyword"
            allow-clear
            class="search-input"
            placeholder="搜索编码或名称"
            @press-enter="applyFilters"
          />
          <a-select v-model:value="statusFilter" class="status-select" @change="applyFilters">
            <a-select-option value="ALL">全部状态</a-select-option>
            <a-select-option value="ACTIVE">已启用</a-select-option>
            <a-select-option value="DISABLED">已禁用</a-select-option>
          </a-select>
          <a-button @click="refreshAll">刷新</a-button>
          <a-button type="primary" @click="openCreateModal">新建供应商</a-button>
        </a-space>
      </header>

      <a-card class="routing-card" title="路由策略" :bordered="false">
        <a-form layout="inline" class="routing-form">
          <a-form-item label="模式">
            <a-radio-group v-model:value="routingForm.mode" @change="handleRoutingModeChange">
              <a-radio-button value="AUTO">AUTO</a-radio-button>
              <a-radio-button value="PIN">PIN</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item v-if="routingForm.mode === 'PIN'" label="固定供应商">
            <a-select
              v-model:value="routingForm.providerCode"
              class="pin-select"
              placeholder="选择供应商"
              @change="saveRouting"
            >
              <a-select-option
                v-for="item in pinCandidates"
                :key="item.providerCode"
                :value="item.providerCode"
              >
                {{ item.providerName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="routingSaving" @click="saveRouting">保存路由</a-button>
          </a-form-item>
        </a-form>
        <div v-if="routingForm.mode === 'AUTO'" class="routing-chain">
          <span class="routing-label">有效链路：</span>
          <template v-if="routing?.effectiveCandidates?.length">
            <span
              v-for="(code, index) in routing.effectiveCandidates"
              :key="code"
              class="routing-node"
            >
              {{ providerDisplayName(code) }}<span v-if="index < routing.effectiveCandidates.length - 1"> → </span>
            </span>
            <span class="routing-node"> → Rule</span>
          </template>
          <span v-else class="routing-empty">暂无已配置 AI 供应商</span>
        </div>
        <div v-else class="routing-chain">
          <span class="routing-label">固定供应商：</span>
          <span>{{ providerDisplayName(routing?.pinnedProviderCode) }}</span>
          <span class="routing-node">（Rule 仍作为确定性 fallback）</span>
        </div>
      </a-card>

      <AdminPageLoading v-if="loading && !providers.length" />

      <AdminPageError v-else-if="pageError" :error="pageError" @retry="refreshAll" />

      <a-card v-else class="table-card" :bordered="false">
        <a-table
          row-key="id"
          size="middle"
          table-layout="fixed"
          :columns="columns"
          :data-source="filteredProviders"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 'max-content' }"
        >
          <template #emptyText>
            <div class="empty-wrap">
              <AdminTableEmpty
                :filtered="hasActiveFilter"
                empty-title="暂无供应商"
                empty-description="尚未配置任何模型供应商"
                @reset="resetFilters"
              />
            </div>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'baseUrl'">
              <a-tooltip :title="record.baseUrl || '—'">
                <span class="cell-ellipsis">{{ record.baseUrl || '—' }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'authMode'">
              {{ providerAuthModeLabel(record.authMode) }}
            </template>
            <template v-else-if="column.key === 'credential'">
              <span v-if="isRuleProvider(record)">—</span>
              <span v-else-if="isProviderConfigured(record)">{{ record.maskedHint || '已配置' }}</span>
              <span v-else class="muted">未配置</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="providerStatusTagColor(record.status)">
                {{ providerStatusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'healthStatus'">
              <a-tag :color="healthStatusColor(record.id)">
                {{ healthStatusLabel(record) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'priority'">
              {{ record.priority ?? '—' }}
            </template>
            <template v-else-if="column.key === 'updatedAt'">
              {{ formatTime(record.updatedAt) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-dropdown>
                <a-button size="small">
                  操作
                  <DownOutlined />
                </a-button>
                <template #overlay>
                  <a-menu @click="handleMenuClick($event, record)">
                    <a-menu-item key="edit">编辑</a-menu-item>
                    <a-menu-item key="toggle">
                      {{ isProviderEnabled(record.status) ? '禁用' : '启用' }}
                    </a-menu-item>
                    <a-menu-item key="health">检查就绪状态</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="modalOpen"
        :confirm-loading="saving"
        title="新建供应商"
        destroy-on-close
        @ok="submitCreateProvider"
      >
        <a-form ref="providerFormRef" :model="providerForm" layout="vertical">
          <a-form-item label="供应商编码" name="providerCode" :rules="[{ required: true, message: '请输入供应商编码' }]">
            <a-input v-model:value="providerForm.providerCode" />
          </a-form-item>
          <a-form-item label="供应商名称" name="providerName" :rules="[{ required: true, message: '请输入供应商名称' }]">
            <a-input v-model:value="providerForm.providerName" />
          </a-form-item>
          <a-form-item label="Base URL" name="baseUrl">
            <a-input v-model:value="providerForm.baseUrl" placeholder="https://api.deepseek.com/v1" />
          </a-form-item>
          <a-form-item label="鉴权模式" name="authMode" :rules="[{ required: true, message: '请输入鉴权模式' }]">
            <a-input v-model:value="providerForm.authMode" placeholder="API_KEY" />
          </a-form-item>
        </a-form>
      </a-modal>

      <a-drawer
        v-model:open="drawerOpen"
        title="编辑供应商"
        width="480"
        destroy-on-close
        :footer-style="{ textAlign: 'right' }"
      >
        <a-form ref="editFormRef" :model="editForm" layout="vertical">
          <a-form-item label="供应商编码">
            <a-input :value="editForm.providerCode" disabled />
          </a-form-item>
          <a-form-item label="供应商名称" name="providerName" :rules="[{ required: true, message: '请输入供应商名称' }]">
            <a-input v-model:value="editForm.providerName" />
          </a-form-item>
          <a-form-item label="Base URL" name="baseUrl">
            <a-input v-model:value="editForm.baseUrl" />
          </a-form-item>
          <a-form-item label="默认模型" name="defaultModel">
            <a-input v-model:value="editForm.defaultModel" />
          </a-form-item>
          <a-form-item label="优先级" name="priority" extra="数值越小，优先级越高">
            <a-input-number v-model:value="editForm.priority" :min="1" :max="999" class="full-width" />
          </a-form-item>
          <a-form-item label="状态" name="status">
            <a-select v-model:value="editForm.status">
              <a-select-option value="ACTIVE">已启用</a-select-option>
              <a-select-option value="DISABLED">已禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="鉴权模式" name="authMode" :rules="[{ required: true, message: '请输入鉴权模式' }]">
            <a-input v-model:value="editForm.authMode" />
          </a-form-item>
          <a-form-item v-if="!isRuleProvider(editTarget)" label="凭据来源" name="credentialSource">
            <a-select v-model:value="editForm.credentialSource">
              <a-select-option value="ENV">ENV</a-select-option>
              <a-select-option value="ENCRYPTED_DB">ENCRYPTED_DB</a-select-option>
            </a-select>
          </a-form-item>
          <template v-if="!isRuleProvider(editTarget)">
            <a-divider>API Key（仅写入）</a-divider>
            <div v-if="isProviderConfigured(editTarget)" class="credential-status">
              <span>已配置 {{ editTarget?.maskedHint || '' }}</span>
            </div>
            <a-form-item :label="isProviderConfigured(editTarget) ? '更新密钥' : '输入 API Key'">
              <a-input-password
                v-model:value="credentialInput"
                placeholder="输入后保存，不会显示完整密钥"
                autocomplete="new-password"
              />
            </a-form-item>
            <a-space>
              <a-button :loading="credentialSaving" @click="saveCredential">保存密钥</a-button>
              <a-button
                v-if="isProviderConfigured(editTarget)"
                danger
                :loading="credentialDeleting"
                @click="deleteCredential"
              >
                删除密钥
              </a-button>
            </a-space>
          </template>
        </a-form>
        <template #footer>
          <a-space>
            <a-button @click="drawerOpen = false">取消</a-button>
            <a-button type="primary" :loading="saving" @click="submitEditProvider">保存</a-button>
          </a-space>
        </template>
      </a-drawer>
    </div>
  </AdminPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import {
  createModelProvider,
  deleteProviderCredential,
  getAiRouting,
  healthCheckModelProvider,
  listModelProviders,
  updateAiRouting,
  updateModelProvider,
  updateModelProviderStatus,
  upsertProviderCredential,
  type AiRoutingConfigResponse,
  type ModelProviderCreateRequest,
  type ModelProviderResponse,
  type ModelProviderUpdateRequest,
} from '@/api/admin'
import { AdminApiError } from '@/types/adminError'
import {
  AdminPageError,
  AdminPageLoading,
  AdminPageShell,
  AdminTableEmpty,
} from '@/components/admin'
import type { LongId } from '@/types/id'
import { providerAuthModeLabel } from '@/utils/adminLabels'
import {
  isProviderEnabled,
  providerStatusLabel,
  providerStatusTagColor,
} from '@/utils/normalizeProviderStatus'
import { formatTime } from '@/utils/time'

type HealthState = 'healthy' | 'unhealthy' | 'unknown'

const loading = ref(true)
const pageError = ref<AdminApiError | null>(null)
const providers = ref<ModelProviderResponse[]>([])
const routing = ref<AiRoutingConfigResponse | null>(null)
const routingSaving = ref(false)
const keyword = ref('')
const statusFilter = ref<'ALL' | 'ACTIVE' | 'DISABLED'>('ALL')
const modalOpen = ref(false)
const drawerOpen = ref(false)
const saving = ref(false)
const credentialSaving = ref(false)
const credentialDeleting = ref(false)
const credentialInput = ref('')
const editingProviderId = ref<LongId>()
const editTarget = ref<ModelProviderResponse>()
const providerFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()
const healthCheckingId = ref<LongId>()
const healthMap = ref<Record<string, HealthState>>({})

const routingForm = reactive({
  mode: 'AUTO' as 'AUTO' | 'PIN',
  providerCode: undefined as string | undefined,
})

const editForm = reactive({
  providerCode: '',
  providerName: '',
  baseUrl: '',
  defaultModel: '',
  priority: 100,
  status: 'ACTIVE',
  authMode: 'API_KEY',
  credentialSource: 'ENV',
})

const columns = [
  { title: '编码', dataIndex: 'providerCode', key: 'providerCode', width: 96, ellipsis: true },
  { title: '名称', dataIndex: 'providerName', key: 'providerName', width: 120, ellipsis: true },
  { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', width: 180, ellipsis: true },
  { title: '鉴权模式', dataIndex: 'authMode', key: 'authMode', width: 96 },
  { title: '凭据', key: 'credential', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 88 },
  { title: '就绪状态', key: 'healthStatus', width: 96 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 72 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 168 },
  { title: '操作', key: 'action', width: 88, fixed: 'right' as const },
]

const hasActiveFilter = computed(
  () => Boolean(keyword.value.trim()) || statusFilter.value !== 'ALL',
)

const pinCandidates = computed(() =>
  providers.value.filter((item) => item.providerCode !== 'rule' && item.providerCode !== 'auto'),
)

const filteredProviders = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return providers.value
    .filter((item) => item.providerCode !== 'auto')
    .filter((item) => {
      const matchKeyword =
        !q ||
        item.providerCode.toLowerCase().includes(q) ||
        item.providerName.toLowerCase().includes(q) ||
        (item.baseUrl ?? '').toLowerCase().includes(q)
      const matchStatus =
        statusFilter.value === 'ALL' ||
        (statusFilter.value === 'ACTIVE' && isProviderEnabled(item.status)) ||
        (statusFilter.value === 'DISABLED' && !isProviderEnabled(item.status))
      return matchKeyword && matchStatus
    })
})

const createEmptyForm = (): ModelProviderCreateRequest => ({
  providerCode: '',
  providerName: '',
  baseUrl: '',
  authMode: 'API_KEY',
})

const providerForm = reactive<ModelProviderCreateRequest>(createEmptyForm())

const isRuleProvider = (record?: ModelProviderResponse) => record?.providerCode === 'rule'

const isProviderConfigured = (record?: ModelProviderResponse) =>
  Boolean(record?.configured ?? record?.apiKeyConfigured)

const providerDisplayName = (code?: string) => {
  if (!code) return '—'
  const found = providers.value.find((item) => item.providerCode === code)
  return found?.providerName ?? code
}

const resetProviderForm = () => {
  Object.assign(providerForm, createEmptyForm())
  editingProviderId.value = undefined
  providerFormRef.value?.clearValidate()
}

const healthStatusLabel = (record: ModelProviderResponse) => {
  const cached = healthMap.value[String(record.id)]
  if (cached === 'healthy') return '已就绪'
  if (cached === 'unhealthy') return '未就绪'
  if (!isProviderConfigured(record) && !isRuleProvider(record)) return '密钥缺失'
  return '未检测'
}

const healthStatusColor = (id: LongId) => {
  const cached = healthMap.value[String(id)]
  if (cached === 'healthy') return 'green'
  if (cached === 'unhealthy') return 'red'
  return 'default'
}

async function loadProviders() {
  loading.value = true
  pageError.value = null
  try {
    const res = await listModelProviders()
    providers.value = res.data.data ?? []
  } catch (error) {
    providers.value = []
    pageError.value = error instanceof AdminApiError ? error : null
  } finally {
    loading.value = false
  }
}

async function loadRouting() {
  try {
    const res = await getAiRouting()
    routing.value = res.data.data ?? null
    if (routing.value) {
      routingForm.mode = routing.value.mode
      routingForm.providerCode = routing.value.pinnedProviderCode
    }
  } catch {
    routing.value = null
  }
}

async function refreshAll() {
  await Promise.all([loadProviders(), loadRouting()])
}

const applyFilters = () => {
  /* client-side only */
}

const resetFilters = () => {
  keyword.value = ''
  statusFilter.value = 'ALL'
}

const openCreateModal = () => {
  resetProviderForm()
  modalOpen.value = true
}

const openEditDrawer = (record: ModelProviderResponse) => {
  editingProviderId.value = record.id
  editTarget.value = record
  credentialInput.value = ''
  editForm.providerCode = record.providerCode
  editForm.providerName = record.providerName
  editForm.baseUrl = record.baseUrl ?? ''
  editForm.defaultModel = record.defaultModel ?? ''
  editForm.priority = record.priority ?? 100
  editForm.status = record.status
  editForm.authMode = record.authMode
  editForm.credentialSource = record.credentialSource ?? 'ENV'
  drawerOpen.value = true
}

const submitCreateProvider = async () => {
  try {
    await providerFormRef.value?.validate()
    saving.value = true
    const payload: ModelProviderCreateRequest = {
      providerCode: providerForm.providerCode.trim(),
      providerName: providerForm.providerName.trim(),
      baseUrl: providerForm.baseUrl?.trim() || undefined,
      authMode: providerForm.authMode.trim(),
    }
    const res = await createModelProvider(payload)
    if (res.data.code === 0) {
      message.success('供应商已创建')
      modalOpen.value = false
      resetProviderForm()
      await refreshAll()
      return
    }
    message.error(res.data.message || '保存失败')
  } catch (error) {
    if (error instanceof Error && error.message) {
      message.error(error.message)
    }
  } finally {
    saving.value = false
  }
}

const submitEditProvider = async () => {
  if (!editingProviderId.value) return
  try {
    await editFormRef.value?.validate()
    saving.value = true
    const payload: ModelProviderUpdateRequest = {
      providerName: editForm.providerName.trim(),
      baseUrl: editForm.baseUrl?.trim() || undefined,
      authMode: editForm.authMode.trim(),
      defaultModel: editForm.defaultModel?.trim() || undefined,
      priority: editForm.priority,
      status: editForm.status,
      credentialSource: isRuleProvider(editTarget.value) ? undefined : editForm.credentialSource,
    }
    const res = await updateModelProvider(editingProviderId.value, payload)
    if (res.data.code === 0) {
      message.success('供应商已更新')
      drawerOpen.value = false
      await refreshAll()
      return
    }
    message.error(res.data.message || '保存失败')
  } catch (error) {
    if (error instanceof Error && error.message) {
      message.error(error.message)
    }
  } finally {
    saving.value = false
  }
}

const saveCredential = async () => {
  if (!editingProviderId.value || !credentialInput.value.trim()) {
    message.warning('请输入 API Key')
    return
  }
  credentialSaving.value = true
  try {
    const res = await upsertProviderCredential(editingProviderId.value, {
      apiKey: credentialInput.value.trim(),
    })
    if (res.data.code === 0 && res.data.data) {
      message.success('密钥已保存')
      credentialInput.value = ''
      if (editTarget.value) {
        editTarget.value = {
          ...editTarget.value,
          configured: res.data.data.configured,
          credentialSource: res.data.data.source,
          maskedHint: res.data.data.maskedHint,
          apiKeyConfigured: res.data.data.configured,
        }
      }
      await refreshAll()
      return
    }
    message.error(res.data.message || '密钥保存失败')
  } catch {
    message.error('密钥保存失败')
  } finally {
    credentialSaving.value = false
  }
}

const deleteCredential = async () => {
  if (!editingProviderId.value) return
  credentialDeleting.value = true
  try {
    const res = await deleteProviderCredential(editingProviderId.value)
    if (res.data.code === 0) {
      message.success('密钥已删除')
      credentialInput.value = ''
      if (editTarget.value) {
        editTarget.value = {
          ...editTarget.value,
          configured: false,
          apiKeyConfigured: false,
          maskedHint: undefined,
        }
      }
      await refreshAll()
      return
    }
    message.error(res.data.message || '密钥删除失败')
  } catch {
    message.error('密钥删除失败')
  } finally {
    credentialDeleting.value = false
  }
}

const handleRoutingModeChange = () => {
  if (routingForm.mode === 'AUTO') {
    routingForm.providerCode = undefined
  }
}

const saveRouting = async () => {
  routingSaving.value = true
  try {
    const res = await updateAiRouting({
      mode: routingForm.mode,
      providerCode: routingForm.mode === 'PIN' ? routingForm.providerCode : undefined,
    })
    if (res.data.code === 0) {
      message.success('路由策略已更新')
      routing.value = res.data.data ?? null
      await loadRouting()
      return
    }
    message.error(res.data.message || '路由保存失败')
  } catch {
    message.error('路由保存失败')
  } finally {
    routingSaving.value = false
  }
}

const toggleStatus = async (record: ModelProviderResponse) => {
  const nextStatus = isProviderEnabled(record.status) ? 'DISABLED' : 'ACTIVE'
  try {
    const res = await updateModelProviderStatus(record.id, { status: nextStatus })
    if (res.data.code === 0) {
      message.success(nextStatus === 'ACTIVE' ? '已启用' : '已禁用')
      await refreshAll()
      return
    }
    message.error(res.data.message || '状态更新失败')
  } catch {
    message.error('状态更新失败')
  }
}

const runHealthCheck = async (record: ModelProviderResponse) => {
  healthCheckingId.value = record.id
  try {
    const res = await healthCheckModelProvider(record.id)
    if (res.data.code === 0 && res.data.data) {
      const result = res.data.data
      healthMap.value[String(record.id)] = result.healthy ? 'healthy' : 'unhealthy'
      if (result.healthy) {
        message.success(result.message || '就绪检查通过')
      } else {
        message.warning(result.message || '就绪检查未通过')
      }
      return
    }
    message.error(res.data.message || '就绪检查失败')
  } catch {
    message.error('就绪检查失败')
  } finally {
    healthCheckingId.value = undefined
  }
}

const handleMenuClick = (
  event: { key: string | number },
  record: ModelProviderResponse,
) => {
  void handleAction(String(event.key), record)
}

const handleAction = async (key: string, record: ModelProviderResponse) => {
  if (key === 'edit') {
    openEditDrawer(record)
    return
  }
  if (key === 'toggle') {
    await toggleStatus(record)
    return
  }
  if (key === 'health') {
    await runHealthCheck(record)
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.provider-page {
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
.routing-card {
  margin-bottom: 16px;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.routing-form {
  margin-bottom: 12px;
}
.routing-chain {
  color: #374151;
  font-size: 14px;
}
.routing-label {
  color: #6b7280;
  margin-right: 8px;
}
.routing-node {
  font-weight: 500;
}
.routing-empty {
  color: #9ca3af;
}
.pin-select {
  min-width: 180px;
}
.search-input {
  width: 200px;
}
.status-select {
  width: 120px;
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
.credential-status {
  margin-bottom: 12px;
  color: #059669;
}
.muted {
  color: #9ca3af;
}
.full-width {
  width: 100%;
}
@media (max-width: 768px) {
  .search-input,
  .status-select,
  .pin-select {
    width: 100%;
  }
  .toolbar-actions {
    width: 100%;
  }
}
</style>
