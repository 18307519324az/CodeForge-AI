<template>
  <AdminPageShell>
    <div class="dashboard-page">
      <AdminDegradedBanner />
      <header class="page-header">
        <div class="page-header-main">
          <div>
            <h2 class="page-heading">管理概览</h2>
            <p class="page-subtitle">平台模型调用指标（全量）与供应商分布</p>
          </div>
          <a-button
            v-if="freshnessStatus === 'STALE'"
            type="primary"
            :loading="refreshing"
            @click="handleRefreshMetrics"
          >
            刷新指标
          </a-button>
        </div>
        <a-alert
          v-if="freshnessStatus === 'STALE'"
          type="warning"
          show-icon
          class="freshness-alert"
          message="聚合数据已过期"
          :description="freshnessDescription"
        />
        <p v-else-if="dataAsOfText" class="freshness-meta">数据截至 {{ dataAsOfText }}</p>
      </header>

      <AdminPageLoading v-if="pageLoading" />

      <AdminPageError v-else-if="pageError" :error="pageError" @retry="loadOverview" />

      <template v-else>
        <a-row :gutter="[16, 16]" class="stat-row">
          <a-col :xs="24" :sm="12" :lg="6">
            <a-card class="stat-card" :bordered="false">
              <a-statistic title="请求总数" :value="stats.requestCount" />
            </a-card>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-card class="stat-card" :bordered="false">
              <a-statistic title="成功数" :value="stats.successCount" />
            </a-card>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-card class="stat-card" :bordered="false">
              <a-statistic title="失败数" :value="stats.failedCount" />
            </a-card>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="6">
            <a-card class="stat-card" :bordered="false">
              <div class="success-rate-stat">
                <div class="success-rate-title">成功率</div>
                <div class="success-rate-value">
                  {{ successRateText }}
                </div>
              </div>
            </a-card>
          </a-col>
        </a-row>

        <a-card title="供应商分布" class="info-card" :bordered="false">
          <AdminPartialFailureCard
            v-if="providerBlockFailed"
            :loading="providerRetrying"
            message="供应商分布加载失败，请点击重试"
            @retry="loadProviderBlock"
          />
          <AdminPageLoading v-else-if="providerLoading" />
          <AdminEmpty
            v-else-if="!providers.length"
            title="暂无供应商数据"
            description="请先在模型供应商页面配置并启用供应商"
          />
          <a-table
            v-else
            row-key="id"
            size="middle"
            table-layout="fixed"
            :columns="providerColumns"
            :data-source="providers"
            :pagination="false"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="providerStatusTagColor(record.status)">
                  {{ providerStatusLabel(record.status) }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'apiKeyConfigured'">
                <a-tag :color="record.apiKeyConfigured ? 'green' : 'orange'">
                  {{ record.apiKeyConfigured ? '已配置' : '未配置' }}
                </a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </template>
    </div>
  </AdminPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getMetricSummary, listModelProviders, refreshMetricSummary, type ModelProviderResponse } from '@/api/admin'
import { AdminApiError } from '@/types/adminError'
import {
  AdminDegradedBanner,
  AdminEmpty,
  AdminPageError,
  AdminPageLoading,
  AdminPageShell,
  AdminPartialFailureCard,
} from '@/components/admin'
import {
  providerStatusLabel,
  providerStatusTagColor,
} from '@/utils/normalizeProviderStatus'

const pageLoading = ref(true)
const pageError = ref<AdminApiError | null>(null)
const refreshing = ref(false)
const freshnessStatus = ref<'FRESH' | 'STALE' | 'EMPTY'>('EMPTY')
const dataAsOfText = ref('')
const freshnessDescription = ref('')
const providerBlockFailed = ref(false)
const providerRetrying = ref(false)
const providerLoading = ref(false)
const providers = ref<ModelProviderResponse[]>([])

const stats = reactive({
  requestCount: 0,
  successCount: 0,
  failedCount: 0,
  successRate: 0,
})

const providerColumns = [
  { title: '编码', dataIndex: 'providerCode', key: 'providerCode', width: 120, ellipsis: true },
  { title: '名称', dataIndex: 'providerName', key: 'providerName', width: 160, ellipsis: true },
  { title: '密钥', dataIndex: 'apiKeyConfigured', key: 'apiKeyConfigured', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
]

const successRateText = computed(() => {
  if (stats.requestCount <= 0) {
    return '—'
  }
  const rate = Number(stats.successRate)
  if (!Number.isFinite(rate)) {
    return '0%'
  }
  const percent = rate <= 1 ? rate * 100 : rate
  return `${Math.round(percent * 100) / 100}%`
})

function formatDateTime(value: string | null | undefined) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function normalizeMetrics(data: Record<string, unknown>) {
  stats.requestCount = Number(data.requestCount ?? 0)
  stats.successCount = Number(data.successCount ?? 0)
  stats.failedCount = Number(data.failedCount ?? 0)
  const rawRate = data.successRate
  if (rawRate == null || rawRate === '') {
    stats.successRate = stats.requestCount > 0 ? stats.successCount / stats.requestCount : 0
  } else {
    const parsed = Number(rawRate)
    stats.successRate = Number.isFinite(parsed) ? parsed : 0
  }
  freshnessStatus.value = (data.freshnessStatus as 'FRESH' | 'STALE' | 'EMPTY') ?? 'EMPTY'
  dataAsOfText.value = formatDateTime(data.dataAsOf as string | null | undefined)
  const staleAfterHours = Math.round(Number(data.staleAfterSeconds ?? 129600) / 3600)
  freshnessDescription.value = dataAsOfText.value
    ? `展示数据来自 model_call_log，但日聚合缓存未同步。数据截至 ${dataAsOfText.value}，超过 ${staleAfterHours} 小时未刷新。请点击「刷新指标」同步聚合。`
    : `日聚合缓存未同步，超过 ${staleAfterHours} 小时未刷新。请点击「刷新指标」。`
}

async function handleRefreshMetrics() {
  refreshing.value = true
  try {
    const res = await refreshMetricSummary()
    if (res.data.code === 0) {
      await loadOverview()
    }
  } finally {
    refreshing.value = false
  }
}

async function loadProviderBlock() {
  providerRetrying.value = true
  providerBlockFailed.value = false
  providerLoading.value = true
  try {
    const res = await listModelProviders()
    providers.value = res.data.data ?? []
  } catch {
    providerBlockFailed.value = true
    providers.value = []
  } finally {
    providerRetrying.value = false
    providerLoading.value = false
  }
}

async function loadOverview() {
  pageLoading.value = true
  pageError.value = null
  try {
    const res = await getMetricSummary()
    if (res.data.code === 0 && res.data.data) {
      normalizeMetrics(res.data.data as unknown as Record<string, unknown>)
    } else if (res.data.code !== 0) {
      pageError.value = new AdminApiError(
        { message: res.data.message || '指标加载失败', code: res.data.code },
        res.status,
      )
    }
    await loadProviderBlock()
  } catch (error) {
    pageError.value = error instanceof AdminApiError ? error : null
    if (!pageError.value) {
      providerBlockFailed.value = true
    }
  } finally {
    pageLoading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.dashboard-page {
  width: 100%;
  max-width: 100%;
}
.page-header {
  margin-bottom: 20px;
}
.page-header-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.freshness-alert {
  margin-top: 12px;
}
.freshness-meta {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
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
.stat-row {
  margin-bottom: 24px;
}
.stat-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  min-height: 108px;
}
.success-rate-stat {
  padding-top: 4px;
}
.success-rate-title {
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
  margin-bottom: 4px;
}
.success-rate-value {
  color: rgba(0, 0, 0, 0.88);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}
.info-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
</style>
