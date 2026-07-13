<template>
  <PageShell title="新建应用" description="填写基础信息，创建你的 AI 应用原型">
    <template #actions>
      <button class="cf-btn-secondary" @click="router.push('/apps')">取消</button>
    </template>

    <div class="form-card">
      <a-form :model="form" :label-col="{ span: 4 }" :wrapper-col="{ span: 14 }" @finish="handleCreate">
        <a-form-item label="工作空间" name="workspaceId" :rules="[{ required: true, message: '请选择工作空间' }]">
          <a-space style="width:100%">
            <a-select v-model:value="form.workspaceId" :options="wsOptions" :loading="loadingWs" placeholder="选择工作空间" style="flex:1" size="large" />
            <button v-if="workspaces.length === 0" class="cf-btn-secondary" @click="createDefaultWs" :disabled="creatingWs">
              {{ creatingWs ? '创建中...' : '创建默认工作空间' }}
            </button>
          </a-space>
        </a-form-item>

        <a-form-item label="应用名称" name="name" :rules="[{ required: true, message: '请输入应用名称' }]">
          <a-input v-model:value="form.name" placeholder="例如：客户管理后台" size="large" />
        </a-form-item>

        <a-form-item label="应用描述" name="description">
          <a-textarea v-model:value="form.description" placeholder="描述应用的用途和功能" :rows="2" />
        </a-form-item>

        <a-form-item label="应用类型" name="appType">
          <a-select v-model:value="form.appType" :options="appTypeOptions" size="large" style="width:100%" />
        </a-form-item>

        <a-form-item label="初始需求" name="requirement">
          <a-textarea v-model:value="form.requirement" placeholder="例如：创建一个客户管理后台，包含客户列表、详情编辑和跟进记录" :rows="3" />
        </a-form-item>

        <a-form-item :wrapper-col="{ offset: 4, span: 14 }">
          <button class="cf-btn-primary" type="submit" :disabled="creating" style="height:40px;padding:0 24px">
            {{ creating ? '创建中...' : '创建应用' }}
          </button>
        </a-form-item>
      </a-form>
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { createAiApp } from '@/api/app'
import { listWorkspaces, createWorkspace, type WorkspaceSummaryResponse } from '@/api/workspace'
import PageShell from '@/components/PageShell.vue'
import type { LongId } from '@/types/id'

const router = useRouter()
const creating = ref(false)
const loadingWs = ref(false)
const creatingWs = ref(false)
const workspaces = ref<WorkspaceSummaryResponse[]>([])

const form = reactive({ name: '', description: '', appType: 'WEB_APP', workspaceId: undefined as LongId | undefined, requirement: '' })
const appTypeOptions = [
  { label: 'Web 应用', value: 'WEB_APP' },
  { label: '后台管理', value: 'ADMIN_WEB' },
  { label: '博客', value: 'BLOG' },
  { label: '官网', value: 'OFFICIAL_SITE' },
  { label: '电商', value: 'ECOMMERCE' },
]
const wsOptions = computed(() => workspaces.value.map(w => ({ label: w.name, value: w.id })))

const loadWs = async () => {
  loadingWs.value = true
  try {
    const res = await listWorkspaces({ pageNo: 1, pageSize: 50 })
    if (res.data.code === 0 && res.data.data) {
      workspaces.value = res.data.data.records || []
      if (workspaces.value.length > 0) form.workspaceId = workspaces.value[0].id
    }
  } catch { /* ignore */ } finally { loadingWs.value = false }
}

const createDefaultWs = async () => {
  creatingWs.value = true
  try {
    const res = await createWorkspace({ name: '默认工作空间', description: '用于管理 CodeForge AI 应用' })
    if (res.data.code === 0 && res.data.data) { message.success('默认工作空间创建成功'); await loadWs(); if (res.data.data.id) form.workspaceId = res.data.data.id; return }
    message.error(res.data.message || '创建失败')
  } catch (e: any) { message.error(e?.message || '创建失败') } finally { creatingWs.value = false }
}

const handleCreate = async () => {
  if (!form.workspaceId) { message.warning('请选择工作空间'); return }
  creating.value = true
  try {
    const res = await createAiApp({ workspaceId: form.workspaceId, name: form.name.trim(), description: form.description.trim() || undefined, appType: form.appType })
    if (res.data.code === 0 && res.data.data) { message.success('应用创建成功'); router.push(`/apps/${String(res.data.data.id)}`); return }
    message.error(res.data.message || '创建失败')
  } catch (e: any) { message.error(e?.message || '创建失败') } finally { creating.value = false }
}

onMounted(() => loadWs())
</script>

<style scoped>
.form-card { background: var(--cf-surface); border: 1px solid var(--cf-border); border-radius: 14px; padding: 24px; }
</style>
