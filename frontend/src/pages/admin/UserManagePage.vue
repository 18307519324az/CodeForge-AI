<template>
  <AdminPageShell>
    <div class="user-manage-page">
      <header class="page-header">
        <div>
          <h2 class="page-heading">用户管理</h2>
          <p class="page-subtitle">管理平台用户账号、状态与角色</p>
        </div>
      </header>

      <a-row v-if="summaryVisible" :gutter="[16, 16]" class="summary-row">
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card class="summary-card" :bordered="false">
            <a-statistic title="用户总数" :value="summary.total" />
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card class="summary-card" :bordered="false">
            <a-statistic title="正常用户" :value="summary.active" />
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card class="summary-card" :bordered="false">
            <a-statistic title="禁用用户" :value="summary.disabled" />
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card class="summary-card" :bordered="false">
            <a-statistic title="平台管理员" :value="summary.admins" />
          </a-card>
        </a-col>
      </a-row>

      <a-card class="table-card" :bordered="false">
        <a-form layout="inline" class="filter-bar" @finish="reloadUsers">
          <a-form-item label="关键词">
            <a-input
              v-model:value="userQuery.keyword"
              allow-clear
              class="filter-keyword"
              placeholder="搜索账号、显示名称或邮箱"
              @press-enter="reloadUsers"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="statusFilter" allow-clear class="filter-select" placeholder="全部状态">
              <a-select-option value="ACTIVE">正常</a-select-option>
              <a-select-option value="DISABLED">已禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="角色">
            <a-select v-model:value="roleFilter" allow-clear class="filter-select" placeholder="全部角色">
              <a-select-option value="PLATFORM_ADMIN">平台管理员</a-select-option>
              <a-select-option value="USER">普通用户</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-space wrap>
              <a-button type="primary" html-type="submit" :loading="loading">搜索</a-button>
              <a-button @click="resetUsers">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>

        <a-table
          row-key="id"
          size="middle"
          table-layout="fixed"
          :columns="userColumns"
          :data-source="pagedUsers"
          :loading="loading"
          :pagination="tablePagination"
          :scroll="{ x: 'max-content' }"
          @change="handleUserTableChange"
        >
          <template #emptyText>
            <div class="empty-wrap">
              <AdminTableEmpty
                :filtered="hasUserFilters"
                :empty-title="userEmptyTitle"
                :empty-description="userEmptyHint"
                @reset="resetUsers"
              />
            </div>
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'email'">
              <a-tooltip :title="record.email || '—'">
                <span class="cell-ellipsis">{{ record.email || '—' }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="userStatusColor(record.status)">
                {{ userStatusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'platformRoles'">
              <a-space wrap>
                <a-tag v-for="role in record.platformRoles" :key="role" color="blue">
                  {{ platformRoleLabel(role) }}
                </a-tag>
                <span v-if="!record.platformRoles?.length">—</span>
              </a-space>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
            <template v-else-if="column.key === 'lastLoginAt'">
              {{ formatTime(record.lastLoginAt) }}
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-drawer v-model:open="detailOpen" title="用户详情" width="480" destroy-on-close>
        <a-descriptions v-if="detailUser" :column="1" bordered size="small">
          <a-descriptions-item label="账号">{{ detailUser.account }}</a-descriptions-item>
          <a-descriptions-item label="显示名称">{{ detailUser.displayName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="邮箱">{{ detailUser.email || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="userStatusColor(detailUser.status)">
              {{ userStatusLabel(detailUser.status) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="平台角色">
            <a-space wrap>
              <a-tag v-for="role in detailUser.platformRoles" :key="role" color="blue">
                {{ platformRoleLabel(role) }}
              </a-tag>
              <span v-if="!detailUser.platformRoles?.length">—</span>
            </a-space>
          </a-descriptions-item>
          <a-descriptions-item label="用户 ID">{{ detailUser.id }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(detailUser.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="最近登录">{{ formatTime(detailUser.lastLoginAt) }}</a-descriptions-item>
        </a-descriptions>
      </a-drawer>
    </div>
  </AdminPageShell>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { listAdminUsers, type AdminUserListItemResponse } from '@/api/admin'
import { AdminPageShell, AdminTableEmpty } from '@/components/admin'
import { adminEmptyMessages } from '@/locales/zh-CN/admin/empty'
import {
  platformRoleLabel,
  userStatusColor,
  userStatusLabel,
} from '@/utils/adminLabels'
import { formatTime } from '@/utils/time'

const SUMMARY_FETCH_SIZE = 200

const userEmptyTitle = adminEmptyMessages.users
const userEmptyHint = adminEmptyMessages.usersHint

const loading = ref(false)
const allUsers = ref<AdminUserListItemResponse[]>([])
const summaryUsers = ref<AdminUserListItemResponse[]>([])
const userTotal = ref(0)
const statusFilter = ref<string>()
const roleFilter = ref<string>()
const detailOpen = ref(false)
const detailUser = ref<AdminUserListItemResponse | null>(null)

const userQuery = reactive({
  keyword: undefined as string | undefined,
  pageNo: 1,
  pageSize: 10,
})

const summary = computed(() => {
  const source = summaryUsers.value
  return {
    total: userTotal.value,
    active: source.filter((item) => item.status === 'ACTIVE').length,
    disabled: source.filter((item) => item.status === 'DISABLED').length,
    admins: source.filter((item) => item.platformRoles?.includes('PLATFORM_ADMIN')).length,
  }
})

const summaryVisible = computed(() => userTotal.value > 0 || summaryUsers.value.length > 0)

const hasUserFilters = computed(
  () =>
    Boolean(userQuery.keyword?.trim()) ||
    Boolean(statusFilter.value) ||
    Boolean(roleFilter.value),
)

const filteredUsers = computed(() => {
  const keyword = userQuery.keyword?.trim().toLowerCase()
  return allUsers.value.filter((user) => {
    const matchKeyword =
      !keyword ||
      user.account.toLowerCase().includes(keyword) ||
      (user.displayName ?? '').toLowerCase().includes(keyword) ||
      (user.email ?? '').toLowerCase().includes(keyword)
    const matchStatus = !statusFilter.value || user.status === statusFilter.value
    const matchRole =
      !roleFilter.value || user.platformRoles?.includes(roleFilter.value)
    return matchKeyword && matchStatus && matchRole
  })
})

const pagedUsers = computed(() => {
  const start = (userQuery.pageNo - 1) * userQuery.pageSize
  return filteredUsers.value.slice(start, start + userQuery.pageSize)
})

const tablePagination = computed<TablePaginationConfig>(() => ({
  current: userQuery.pageNo,
  pageSize: userQuery.pageSize,
  total: filteredUsers.value.length,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (value) => `共 ${value} 条`,
}))

const userColumns: TableColumnsType<AdminUserListItemResponse> = [
  { title: '用户 ID', dataIndex: 'id', key: 'id', width: 108, ellipsis: true },
  { title: '账号', dataIndex: 'account', key: 'account', width: 120, ellipsis: true },
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName', width: 120, ellipsis: true },
  { title: '邮箱', dataIndex: 'email', key: 'email', width: 180, ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 88 },
  { title: '平台角色', dataIndex: 'platformRoles', key: 'platformRoles', width: 140 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 168 },
  { title: '最近登录', dataIndex: 'lastLoginAt', key: 'lastLoginAt', width: 168 },
  { title: '操作', key: 'action', width: 72, fixed: 'right' },
]

const loadSummaryUsers = async () => {
  try {
    const res = await listAdminUsers({ pageNo: 1, pageSize: SUMMARY_FETCH_SIZE })
    if (res.data.code === 0 && res.data.data) {
      summaryUsers.value = res.data.data.records ?? []
      userTotal.value = Number(res.data.data.total ?? 0)
    }
  } catch (error) {
    console.error('加载用户统计失败', error)
  }
}

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await listAdminUsers({
      keyword: userQuery.keyword?.trim() || undefined,
      pageNo: 1,
      pageSize: SUMMARY_FETCH_SIZE,
    })
    if (res.data.code === 0 && res.data.data) {
      allUsers.value = res.data.data.records ?? []
      if (!userQuery.keyword?.trim()) {
        userTotal.value = Number(res.data.data.total ?? 0)
      }
      return
    }
    message.error(res.data.message || '加载用户列表失败')
  } catch (error) {
    console.error('加载用户列表失败', error)
    message.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const reloadUsers = async () => {
  userQuery.pageNo = 1
  await loadUsers()
}

const resetUsers = async () => {
  userQuery.keyword = undefined
  userQuery.pageNo = 1
  userQuery.pageSize = 10
  statusFilter.value = undefined
  roleFilter.value = undefined
  await Promise.all([loadSummaryUsers(), loadUsers()])
}

const handleUserTableChange = (pager: TablePaginationConfig) => {
  userQuery.pageNo = pager.current || 1
  userQuery.pageSize = pager.pageSize || 10
}

const openDetail = (record: AdminUserListItemResponse) => {
  detailUser.value = record
  detailOpen.value = true
}

onMounted(async () => {
  await Promise.all([loadSummaryUsers(), loadUsers()])
})
</script>

<style scoped>
.user-manage-page {
  width: 100%;
  max-width: 100%;
}
.page-header {
  margin-bottom: 16px;
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
.summary-row {
  margin-bottom: 16px;
}
.summary-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.table-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.filter-bar {
  margin-bottom: 16px;
  row-gap: 12px;
}
.filter-keyword {
  width: 260px;
}
.filter-select {
  width: 140px;
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
  .filter-keyword,
  .filter-select {
    width: 100%;
  }
}
</style>
