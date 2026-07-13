<template>
  <PageShell title="应用广场" description="发现其他用户发布的 AI 应用">
    <div class="explore-filters">
      <a-input
        v-model:value="keyword"
        placeholder="搜索应用"
        allow-clear
        style="max-width: 240px"
        @pressEnter="onFilterChange"
        @change="onKeywordClear"
      />
      <a-select
        v-model:value="appType"
        placeholder="应用类型"
        allow-clear
        :options="appTypeOptions"
        style="max-width: 160px"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="sort"
        :options="sortOptions"
        style="max-width: 140px"
        @change="onFilterChange"
      />
    </div>

    <a-spin :spinning="loading">
      <a-alert v-if="loadError" type="error" :message="loadError" show-icon style="margin-bottom: 16px" />

      <div v-if="apps.length" class="explore-grid">
        <div v-for="item in apps" :key="item.publicationId" class="explore-card">
          <div class="explore-card-header">
            <a-tooltip :title="item.publicTitle">
              <h3 class="explore-card-title">{{ item.publicTitle }}</h3>
            </a-tooltip>
            <a-tag color="blue">{{ appTypeLabel(item.appType) }}</a-tag>
          </div>
          <p class="explore-card-desc">{{ item.publicDescription || '暂无简介' }}</p>
          <div class="explore-card-meta">
            <span>{{ item.publisherDisplayName || '匿名用户' }}</span>
            <span>·</span>
            <span>{{ formatVersionNoFromNumber(item.versionNo) }}</span>
          </div>
          <div class="explore-card-stats">
            <span>浏览 {{ item.viewCount ?? 0 }}</span>
            <span>下载 {{ item.downloadCount ?? 0 }}</span>
            <span v-if="item.allowDownload" class="explore-downloadable">可下载</span>
          </div>
          <div class="explore-card-actions">
            <a-button
              v-if="item.allowPreview"
              type="link"
              size="small"
              :loading="previewingSlug === item.slug"
              @click="handlePreview(item.slug)"
            >
              预览
            </a-button>
            <a-button type="link" size="small" @click="router.push(`/explore/apps/${item.slug}`)">
              查看详情
            </a-button>
            <a-button
              v-if="resolveDownloadUi(item).showAction"
              type="link"
              size="small"
              :disabled="!resolveDownloadUi(item).clickable"
              :loading="downloadingSlug === item.slug"
              @click="handleDownload(item)"
            >
              {{ resolveDownloadUi(item).label }}
            </a-button>
          </div>
        </div>
      </div>

      <EmptyState
        v-else-if="!loading && !loadError"
        title="暂无公开应用"
        description="还没有用户发布应用，成为第一个分享者吧。"
      />
    </a-spin>

    <div v-if="total > 0" class="explore-pagination">
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        show-less-items
        @change="onPageChange"
      />
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listPublicApps, type PublicAppListItemResponse } from '@/api/publicApp'
import { appTypeLabel, formatVersionNoFromNumber } from '@/utils/appLabels'
import { isApiSuccessCode } from '@/utils/apiCode'
import { openPublicAppPreview } from '@/utils/openPublicAppPreview'
import { resolveMarketplaceDownloadUi } from '@/utils/marketplaceDownload'
import { triggerPublicAppDownload } from '@/utils/openPublicAppDownload'

const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const apps = ref<PublicAppListItemResponse[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(12)
const keyword = ref('')
const appType = ref<string | undefined>()
const sort = ref('LATEST')
const previewingSlug = ref('')
const downloadingSlug = ref('')

const resolveDownloadUi = (item: PublicAppListItemResponse) =>
  resolveMarketplaceDownloadUi({
    allowDownload: item.allowDownload,
    downloadAvailability: item.downloadAvailability,
  })

const appTypeOptions = [
  { label: 'Web 应用', value: 'WEB_APP' },
  { label: '后台管理', value: 'ADMIN_WEB' },
  { label: '博客', value: 'BLOG' },
  { label: '官网', value: 'OFFICIAL_SITE' },
]

const sortOptions = [
  { label: '最新发布', value: 'LATEST' },
  { label: '最受欢迎', value: 'POPULAR' },
]

const loadApps = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listPublicApps({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      appType: appType.value,
      sort: sort.value,
    })
    if (!isApiSuccessCode(response.data.code)) {
      loadError.value = response.data.message || '加载失败'
      apps.value = []
      total.value = 0
      return
    }
    apps.value = response.data.data?.records ?? []
    total.value = Number(response.data.data?.total ?? 0)
  } catch {
    loadError.value = '加载应用广场失败'
    apps.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const onFilterChange = () => {
  pageNo.value = 1
  loadApps()
}

const onKeywordClear = () => {
  if (!keyword.value) onFilterChange()
}

const onPageChange = (nextPage: number) => {
  pageNo.value = nextPage
  loadApps()
}

const handlePreview = async (slug?: string) => {
  if (!slug) return
  previewingSlug.value = slug
  try {
    await openPublicAppPreview(slug)
  } finally {
    previewingSlug.value = ''
  }
}

const handleDownload = async (item: PublicAppListItemResponse) => {
  if (!item.slug) return
  downloadingSlug.value = item.slug
  try {
    await triggerPublicAppDownload(item.slug, {
      allowDownload: item.allowDownload,
      downloadAvailability: item.downloadAvailability,
    })
  } finally {
    downloadingSlug.value = ''
  }
}

onMounted(loadApps)
</script>

<style scoped>
.explore-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.explore-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.explore-card {
  border: 1px solid var(--cf-border);
  border-radius: 12px;
  padding: 16px;
  background: var(--cf-surface);
}
.explore-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.explore-card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.explore-card-desc {
  margin: 0 0 10px;
  color: var(--cf-text-secondary);
  font-size: 13px;
  min-height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.explore-card-meta,
.explore-card-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--cf-text-tertiary);
  margin-bottom: 8px;
}
.explore-downloadable {
  color: var(--cf-primary);
}
.explore-card-actions {
  display: flex;
  gap: 4px;
}
.explore-pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
