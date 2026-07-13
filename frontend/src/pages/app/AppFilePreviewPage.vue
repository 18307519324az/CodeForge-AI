<template>
  <div class="file-page">
    <div class="page-toolbar">
      <a-space>
        <a-button size="small" @click="router.push(`/apps/${appId}`)">← 返回详情</a-button>
        <h2 class="page-heading">浏览生成文件</h2>
        <a-select v-if="versionOptions.length" v-model:value="selectedVersionId" :options="versionOptions" placeholder="选择版本" style="width: 200px" @change="loadFiles" />
      </a-space>
    </div>
    <a-row :gutter="16">
      <a-col :span="6">
        <a-card title="文件列表" size="small" class="panel-card">
          <a-spin :spinning="loadingFiles">
            <div v-if="files.length">
              <div v-for="f in files" :key="f.id" class="file-row" :class="{ active: selectedFile === f.filePath }" @click="selectFile(f)">
                <FileOutlined style="margin-right: 6px; color: #1677ff" />
                <span>{{ f.filePath }}</span>
              </div>
            </div>
            <a-empty v-else description="暂无文件" />
          </a-spin>
        </a-card>
      </a-col>
      <a-col :span="18">
        <a-card :title="selectedFile || '代码预览'" size="small" class="panel-card">
          <a-spin :spinning="loadingContent">
            <pre v-if="fileContent" class="code-preview"><code>{{ fileContent }}</code></pre>
            <a-empty v-else description="选择左侧文件查看内容" />
          </a-spin>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { FileOutlined } from '@ant-design/icons-vue'
import { listAppVersions, listAppVersionFiles, getAppVersionFileContent } from '@/api/appVersion'
import type { LongId } from '@/types/id'

const route = useRoute()
const router = useRouter()
const appId = String(route.params.appId ?? '')
const selectedVersionId = ref<LongId>()
const versionOptions = ref<{ label: string; value: LongId }[]>([])
const files = ref<any[]>([])
const selectedFile = ref('')
const fileContent = ref('')
const loadingFiles = ref(false)
const loadingContent = ref(false)

const loadVersions = async () => {
  try {
    const res = await listAppVersions(appId, { pageNo: 1, pageSize: 50 })
    if (res.data.code === 0 && res.data.data) {
      versionOptions.value = (res.data.data.records || []).map((v: any) => ({ label: `v${v.versionNo}${v.changeSummary ? ' - ' + v.changeSummary : ''}`, value: v.id }))
      if (!selectedVersionId.value && versionOptions.value.length) {
        selectedVersionId.value = versionOptions.value[0].value
        loadFiles()
      }
    }
  } catch { /* ignore */ }
}

const loadFiles = async () => {
  if (!selectedVersionId.value) return
  loadingFiles.value = true
  try {
    const res = await listAppVersionFiles(appId, selectedVersionId.value)
    if (res.data.code === 0) files.value = res.data.data || []
  } catch (e: any) { message.error(e?.message || '加载文件失败') }
  finally { loadingFiles.value = false }
}

const selectFile = async (f: any) => {
  selectedFile.value = f.filePath
  loadingContent.value = true
  try {
    const res = await getAppVersionFileContent(appId, f.versionId || selectedVersionId.value, f.filePath)
    if (res.data.code === 0 && res.data.data) {
      fileContent.value = (res.data.data as any)?.content || (res.data.data as any)?.fileContent || ''
    }
  } catch { /* ignore */ }
  finally { loadingContent.value = false }
}

onMounted(async () => {
  const queryVersionId = route.query.versionId
  if (queryVersionId) {
    selectedVersionId.value = String(queryVersionId) as LongId
  }
  await loadVersions()
  if (selectedVersionId.value) {
    await loadFiles()
  }
})
</script>

<style scoped>
.file-page { max-width: 1400px; }
.page-toolbar { margin-bottom: 16px; }
.page-heading { margin: 0; font-size: 20px; display: inline; }
.panel-card { border-radius: 12px; height: calc(100vh - 200px); overflow-y: auto; }
.file-row { padding: 6px 8px; cursor: pointer; font-size: 13px; border-radius: 4px; }
.file-row:hover, .file-row.active { background: #e6f4ff; }
.code-preview { background: #1e293b; color: #e2e8f0; padding: 16px; border-radius: 8px; overflow-x: auto; font-size: 13px; line-height: 1.6; max-height: calc(100vh - 280px); }
</style>
