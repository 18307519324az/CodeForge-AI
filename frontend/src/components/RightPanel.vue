<template>
  <div class="right-panel">
    <div class="right-panel-header">
      <span class="right-panel-title">上下文</span>
      <button class="right-panel-close" @click="emit('close')">&times;</button>
    </div>

    <div class="panel-body">
      <div class="panel-section">
        <div class="section-label">当前应用</div>
        <div v-if="app">
          <div class="app-name">{{ app.name }}</div>
          <div style="margin-top:4px">
            <a-tag>{{ app.appType || 'Web 应用' }}</a-tag>
            <a-tag :color="statusColor(app.status)">{{ statusLabel(app.status) }}</a-tag>
          </div>
          <div class="app-actions">
            <a-button type="link" size="small" @click="router.push(`/apps/${app.id}`)">详情</a-button>
            <a-button type="link" size="small" @click="router.push(`/apps/${app.id}/versions`)">版本</a-button>
          </div>
        </div>
        <div v-else class="empty-card">
          <div class="empty-title">还没有选中的应用</div>
          <div class="empty-desc">输入需求并点击"生成应用"后，这里会展示应用状态、版本快照、文件列表和导出入口。</div>
        </div>
      </div>

      <div class="panel-section" v-if="versions.length">
        <div class="section-label">版本快照</div>
        <div v-for="v in versions.slice(0, 5)" :key="v.id" class="item-row">
          <span>v{{ v.versionNo }}</span>
          <span class="item-meta">{{ v.changeSummary || '-' }}</span>
        </div>
      </div>

      <div class="panel-section" v-if="files.length">
        <div class="section-label">生成文件</div>
        <div v-for="f in files.slice(0, 10)" :key="f.id" class="file-row">
          <FileOutlined class="file-icon" />
          <span class="file-name">{{ f.filePath || f.fileName }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { FileOutlined } from '@ant-design/icons-vue'
import { listAppVersions } from '@/api/appVersion'
import type { AiAppDetailResponse } from '@/api/app'

const router = useRouter()
const props = defineProps<{ app: AiAppDetailResponse | null }>()
const emit = defineEmits<{ close: [] }>()

const versions = ref<any[]>([])
const files = ref<any[]>([])

const statusColor = (s?: string) => ({ DRAFT: 'default', DEVELOPING: 'blue', RELEASED: 'green', ARCHIVED: 'red' }[s||''] || 'default')
const statusLabel = (s?: string) => ({ DRAFT: '草稿', DEVELOPING: '开发中', RELEASED: '已发布', ARCHIVED: '已归档' }[s||''] || s||'-')

watch(() => props.app, async (a) => {
  if (a?.id) {
    try {
      const r = await listAppVersions(a.id, { pageNo: 1, pageSize: 5 })
      if (r.data.code === 0 && r.data.data) versions.value = r.data.data.records || []
    } catch { /* ignore */ }
  } else { versions.value = []; files.value = [] }
}, { immediate: true })
</script>

<style scoped>
.right-panel { display: flex; flex-direction: column; height: 100%; }
.right-panel-header { height: 56px; min-height: 56px; padding: 0 16px; border-bottom: 1px solid var(--cf-border); display: flex; align-items: center; justify-content: space-between; }
.right-panel-title { font-size: 14px; font-weight: 600; color: var(--cf-text); }
.right-panel-close { width: 28px; height: 28px; border: none; border-radius: 6px; background: transparent; color: var(--cf-text-secondary); cursor: pointer; font-size: 18px; display: flex; align-items: center; justify-content: center; }
.right-panel-close:hover { background: var(--cf-surface-hover); color: var(--cf-text); }
.panel-body { padding: 20px; overflow-y: auto; flex: 1; }
.panel-section { margin-bottom: 20px; }
.section-label { font-size: 11px; font-weight: 600; color: var(--cf-text-tertiary); text-transform: uppercase; margin-bottom: 8px; letter-spacing: 0.5px; }
.app-name { font-weight: 600; font-size: 14px; color: var(--cf-text); }
.app-actions { margin-top: 8px; display: flex; gap: 4px; }
.empty-card { border: 1px solid var(--cf-border); border-radius: 12px; padding: 14px; background: var(--cf-bg); }
.empty-title { font-size: 14px; font-weight: 600; color: var(--cf-text); margin-bottom: 6px; }
.empty-desc { font-size: 13px; line-height: 1.6; color: var(--cf-text-secondary); }
.item-row { display: flex; justify-content: space-between; padding: 3px 0; font-size: 13px; color: var(--cf-text-secondary); border-bottom: 1px solid var(--cf-border); }
.item-meta { color: var(--cf-text-tertiary); font-size: 12px; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-row { display: flex; align-items: center; gap: 6px; padding: 3px 0; font-size: 13px; color: var(--cf-text-secondary); }
.file-icon { color: var(--cf-text); font-size: 13px; }
.file-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
