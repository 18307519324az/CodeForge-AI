<template>
  <PageShell :title="detail?.publicTitle || '公开应用'" description="查看公开应用详情并在线体验">
    <a-spin :spinning="loading">
      <a-result
        v-if="!loading && loadError"
        status="404"
        title="应用不可用"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" @click="router.push('/explore')">返回应用广场</a-button>
        </template>
      </a-result>

      <div v-else-if="detail" class="public-detail">
        <div class="public-detail-header">
          <div>
            <h1 class="public-detail-title">{{ detail.publicTitle }}</h1>
            <div class="public-detail-meta">
              <span>{{ detail.publisherDisplayName || '匿名用户' }}</span>
              <span>·</span>
              <span>{{ formatVersionNoFromNumber(detail.versionNo) }}</span>
              <span>·</span>
              <span>{{ formatTime(detail.publishedAt) }}</span>
            </div>
          </div>
          <div class="public-detail-actions">
            <a-button
              v-if="detail.allowPreview"
              type="primary"
              :loading="openingPreview"
              @click="handlePreview"
            >
              在线预览
            </a-button>
            <a-button
              v-if="downloadUi.showAction"
              :loading="downloading"
              :disabled="!downloadUi.clickable"
              @click="handleDownload"
            >
              {{ downloadUi.label }}
            </a-button>
          </div>
        </div>

        <a-card :bordered="false" class="public-detail-card">
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="公开简介">
              {{ detail.publicDescription || '暂无简介' }}
            </a-descriptions-item>
            <a-descriptions-item label="应用类型">
              {{ appTypeLabel(detail.appType) }}
            </a-descriptions-item>
            <a-descriptions-item v-if="detail.generationSource" label="生成来源">
              {{ versionSourceLabel(detail.generationSource) }}
            </a-descriptions-item>
            <a-descriptions-item label="浏览次数">
              {{ detail.viewCount ?? 0 }}
            </a-descriptions-item>
            <a-descriptions-item label="下载次数">
              {{ detail.downloadCount ?? 0 }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </div>
    </a-spin>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '@/components/PageShell.vue'
import {
  getPublicAppDetail,
  recordPublicAppView,
  type PublicAppDetailResponse,
} from '@/api/publicApp'
import {
  appTypeLabel,
  formatVersionNoFromNumber,
  versionSourceLabel,
} from '@/utils/appLabels'
import { formatTime } from '@/utils/time'
import { isApiSuccessCode } from '@/utils/apiCode'
import { openPublicAppPreview } from '@/utils/openPublicAppPreview'
import { resolveMarketplaceDownloadUi } from '@/utils/marketplaceDownload'
import { triggerPublicAppDownload } from '@/utils/openPublicAppDownload'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const detail = ref<PublicAppDetailResponse | null>(null)
const openingPreview = ref(false)
const downloading = ref(false)
const viewRecorded = ref(false)

const downloadUi = computed(() =>
  resolveMarketplaceDownloadUi({
    allowDownload: detail.value?.allowDownload,
    downloadAvailability: detail.value?.downloadAvailability,
  }),
)

const loadDetail = async () => {
  const slug = String(route.params.slug || '')
  if (!slug) {
    loadError.value = '应用标识无效'
    return
  }

  loading.value = true
  loadError.value = ''
  try {
    const response = await getPublicAppDetail(slug)
    if (!isApiSuccessCode(response.data.code) || !response.data.data) {
      loadError.value = response.data.message || '应用不存在或未发布'
      detail.value = null
      return
    }
    detail.value = response.data.data
    if (!viewRecorded.value) {
      viewRecorded.value = true
      try {
        const viewResponse = await recordPublicAppView(slug)
        if (isApiSuccessCode(viewResponse.data.code) && viewResponse.data.data && detail.value) {
          detail.value = {
            ...detail.value,
            viewCount: Number(viewResponse.data.data.viewCount ?? detail.value.viewCount ?? 0),
          }
        }
      } catch {
        // View analytics failure must not block public detail rendering.
      }
    }
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    loadError.value = err.response?.data?.message || '应用不存在或未发布'
    detail.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

watch(
  () => route.params.slug,
  (nextSlug, prevSlug) => {
    if (nextSlug && nextSlug !== prevSlug) {
      viewRecorded.value = false
      loadDetail()
    }
  },
)

const handlePreview = async () => {
  if (!detail.value?.slug) return
  openingPreview.value = true
  try {
    await openPublicAppPreview(detail.value.slug)
  } finally {
    openingPreview.value = false
  }
}

const handleDownload = async () => {
  if (!detail.value?.slug) return
  downloading.value = true
  try {
    await triggerPublicAppDownload(detail.value.slug, {
      allowDownload: detail.value.allowDownload,
      downloadAvailability: detail.value.downloadAvailability,
    })
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.public-detail-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.public-detail-title {
  margin: 0 0 8px;
  font-size: 24px;
}
.public-detail-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--cf-text-secondary);
  font-size: 13px;
}
.public-detail-actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.public-detail-card {
  border: 1px solid var(--cf-border);
  border-radius: 12px;
}
</style>
