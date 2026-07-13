<template>
  <div id="chatManagePage">
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="modelCallLogs" tab="Model Call Logs">
        <a-card title="Model Call Logs">
          <a-form layout="inline" :model="modelCallQuery" @finish="reloadModelCallLogs">
            <a-form-item label="Keyword">
              <a-input
                v-model:value="modelCallQuery.keyword"
                allow-clear
                placeholder="Search requestId, modelName, or status"
                style="width: 280px"
              />
            </a-form-item>
            <a-form-item>
              <a-space>
                <a-button type="primary" html-type="submit">Search</a-button>
                <a-button @click="resetModelCallLogs">Reset</a-button>
              </a-space>
            </a-form-item>
          </a-form>

          <a-table
            :columns="modelCallColumns"
            :data-source="modelCallLogs"
            :pagination="modelCallPagination"
            :scroll="{ x: 1280 }"
            row-key="id"
            style="margin-top: 16px"
            @change="handleModelCallTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'createdAt'">
                {{ formatTime(record.createdAt) }}
              </template>
              <template v-else-if="column.dataIndex === 'errorMessage'">
                <span class="error-message">{{ record.errorMessage || '-' }}</span>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>

      <a-tab-pane key="auditLogs" tab="Audit Logs">
        <a-card title="Audit Logs">
          <a-table
            :columns="auditColumns"
            :data-source="auditLogs"
            :pagination="auditPagination"
            :scroll="{ x: 1080 }"
            row-key="id"
            @change="handleAuditTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'createdAt'">
                {{ formatTime(record.createdAt) }}
              </template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  listAuditLogs,
  listModelCallLogs,
  type AuditLogResponse,
  type ModelCallLogResponse,
} from '@/api/admin'
import { formatTime } from '@/utils/time'

const activeTab = ref('modelCallLogs')

const modelCallLogs = ref<ModelCallLogResponse[]>([])
const modelCallTotal = ref(0)
const auditLogs = ref<AuditLogResponse[]>([])
const auditTotal = ref(0)

const modelCallQuery = reactive({
  keyword: undefined as string | undefined,
  pageNo: 1,
  pageSize: 10,
})

const auditQuery = reactive({
  pageNo: 1,
  pageSize: 10,
})

const modelCallColumns = [
  { title: 'ID', dataIndex: 'id' },
  { title: 'Task ID', dataIndex: 'taskId' },
  { title: 'Provider ID', dataIndex: 'providerId' },
  { title: 'Model Name', dataIndex: 'modelName' },
  { title: 'Request ID', dataIndex: 'requestId' },
  { title: 'Status', dataIndex: 'status' },
  { title: 'Input Tokens', dataIndex: 'inputTokens' },
  { title: 'Output Tokens', dataIndex: 'outputTokens' },
  { title: 'Duration (ms)', dataIndex: 'durationMs' },
  { title: 'Error Message', dataIndex: 'errorMessage' },
  { title: 'Created At', dataIndex: 'createdAt' },
]

const auditColumns = [
  { title: 'ID', dataIndex: 'id' },
  { title: 'Workspace ID', dataIndex: 'workspaceId' },
  { title: 'Operator User ID', dataIndex: 'operatorUserId' },
  { title: 'Action Type', dataIndex: 'actionType' },
  { title: 'Target Type', dataIndex: 'targetType' },
  { title: 'Target ID', dataIndex: 'targetId' },
  { title: 'Request ID', dataIndex: 'requestId' },
  { title: 'Created At', dataIndex: 'createdAt' },
]

const loadModelCallLogs = async () => {
  try {
    const res = await listModelCallLogs({ ...modelCallQuery })
    if (res.data.code === 0 && res.data.data) {
      modelCallLogs.value = res.data.data.records ?? []
      modelCallTotal.value = res.data.data.total ?? 0
      return
    }
    message.error(res.data.message || 'Failed to load model call logs')
  } catch (error) {
    console.error('Failed to load model call logs', error)
    message.error('Failed to load model call logs')
  }
}

const loadAuditLogs = async () => {
  try {
    const res = await listAuditLogs({ ...auditQuery })
    if (res.data.code === 0 && res.data.data) {
      auditLogs.value = res.data.data.records ?? []
      auditTotal.value = res.data.data.total ?? 0
      return
    }
    message.error(res.data.message || 'Failed to load audit logs')
  } catch (error) {
    console.error('Failed to load audit logs', error)
    message.error('Failed to load audit logs')
  }
}

const reloadModelCallLogs = () => {
  modelCallQuery.pageNo = 1
  loadModelCallLogs()
}

const resetModelCallLogs = () => {
  modelCallQuery.keyword = undefined
  modelCallQuery.pageNo = 1
  modelCallQuery.pageSize = 10
  loadModelCallLogs()
}

const handleModelCallTableChange = (page: { current: number; pageSize: number }) => {
  modelCallQuery.pageNo = page.current
  modelCallQuery.pageSize = page.pageSize
  loadModelCallLogs()
}

const handleAuditTableChange = (page: { current: number; pageSize: number }) => {
  auditQuery.pageNo = page.current
  auditQuery.pageSize = page.pageSize
  loadAuditLogs()
}

const modelCallPagination = computed(() => ({
  current: modelCallQuery.pageNo,
  pageSize: modelCallQuery.pageSize,
  total: modelCallTotal.value,
  showSizeChanger: true,
  showTotal: (value: number) => `Total ${value} items`,
}))

const auditPagination = computed(() => ({
  current: auditQuery.pageNo,
  pageSize: auditQuery.pageSize,
  total: auditTotal.value,
  showSizeChanger: true,
  showTotal: (value: number) => `Total ${value} items`,
}))

onMounted(() => {
  loadModelCallLogs()
  loadAuditLogs()
})
</script>

<style scoped>
#chatManagePage {
  padding: 24px;
  background: white;
  margin-top: 16px;
}

.error-message {
  color: #cf1322;
}
</style>
