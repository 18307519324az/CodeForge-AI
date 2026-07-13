<template>
  <div id="appEditPage">
    <div class="page-header">
      <h1>编辑应用</h1>
    </div>

    <div class="edit-container">
      <a-card title="基础信息" :loading="loading">
        <a-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          layout="vertical"
          @finish="handleSubmit"
        >
          <a-form-item label="应用名称" name="name">
            <a-input v-model:value="formData.name" :maxlength="128" show-count />
          </a-form-item>

          <a-form-item label="应用描述" name="description">
            <a-textarea
              v-model:value="formData.description"
              :rows="4"
              :maxlength="512"
              show-count
            />
          </a-form-item>

          <a-form-item label="封面地址" name="coverUrl">
            <a-input v-model:value="formData.coverUrl" placeholder="https://example.com/cover.png" />
          </a-form-item>

          <a-form-item label="可见性" name="visibility">
            <a-select
              v-model:value="formData.visibility"
              :options="[
                { label: '私有', value: 'PRIVATE' },
                { label: '工作空间', value: 'WORKSPACE' },
                { label: '公开', value: 'PUBLIC' },
              ]"
            />
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" html-type="submit" :loading="submitting">
                保存修改
              </a-button>
              <a-button @click="resetForm">重置</a-button>
              <a-button type="link" @click="goToChat">进入任务页</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card title="应用详情" style="margin-top: 24px">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="应用 ID">{{ appInfo?.id }}</a-descriptions-item>
          <a-descriptions-item label="工作空间 ID">{{ appInfo?.workspaceId }}</a-descriptions-item>
          <a-descriptions-item label="应用类型">{{ appInfo?.appType }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ appInfo?.status }}</a-descriptions-item>
          <a-descriptions-item label="当前版本 ID">
            {{ appInfo?.currentVersionId ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="最近任务 ID">
            {{ appInfo?.latestTaskId ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatTime(appInfo?.createdAt) }}
          </a-descriptions-item>
          <a-descriptions-item label="更新时间">
            {{ formatTime(appInfo?.updatedAt) }}
          </a-descriptions-item>
        </a-descriptions>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import { getAiApp, updateAiApp, type AiAppDetailResponse } from '@/api/app'
import { formatTime } from '@/utils/time'

const route = useRoute()
const router = useRouter()

const appInfo = ref<AiAppDetailResponse>()
const loading = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive({
  name: '',
  description: '',
  coverUrl: '',
  visibility: 'PRIVATE',
})

const rules = {
  name: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { min: 1, max: 128, message: '应用名称长度需要在 1 到 128 之间', trigger: 'blur' },
  ],
  description: [{ max: 512, message: '应用描述不能超过 512 个字符', trigger: 'blur' }],
  coverUrl: [{ type: 'url', message: '请输入有效的 URL', trigger: 'blur' }],
}

const fillForm = (app: AiAppDetailResponse) => {
  formData.name = app.name || ''
  formData.description = app.description || ''
  formData.coverUrl = app.coverUrl || ''
  formData.visibility = app.visibility || 'PRIVATE'
}

const fetchAppInfo = async () => {
  const appId = String(route.params.id ?? '')
  if (!appId) {
    message.error('应用 ID 不存在')
    await router.push('/')
    return
  }

  loading.value = true
  try {
    const res = await getAiApp(appId)
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      fillForm(res.data.data)
      return
    }
    message.error(`获取应用失败：${res.data.message}`)
    await router.push('/')
  } catch (error) {
    console.error('获取应用失败', error)
    message.error('获取应用失败')
    await router.push('/')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!appInfo.value?.id) {
    return
  }

  submitting.value = true
  try {
    const res = await updateAiApp(appInfo.value.id, {
      name: formData.name,
      description: formData.description || undefined,
      coverUrl: formData.coverUrl || undefined,
      visibility: formData.visibility,
    })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      fillForm(res.data.data)
      message.success('应用已更新')
      return
    }
    message.error(`更新应用失败：${res.data.message}`)
  } catch (error) {
    console.error('更新应用失败', error)
    message.error('更新应用失败')
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  if (!appInfo.value) {
    return
  }
  fillForm(appInfo.value)
  formRef.value?.clearValidate()
}

const goToChat = () => {
  if (appInfo.value?.id) {
    router.push(`/app/chat/${appInfo.value.id}`)
  }
}

onMounted(() => {
  fetchAppInfo()
})
</script>

<style scoped>
#appEditPage {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.edit-container {
  border-radius: 8px;
}

:deep(.ant-card-head) {
  background: #fafafa;
}

:deep(.ant-descriptions-item-label) {
  background: #fafafa;
  font-weight: 500;
}
</style>
