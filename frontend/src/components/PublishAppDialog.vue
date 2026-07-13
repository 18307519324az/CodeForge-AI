<template>
  <a-modal
    :open="open"
    :title="isPublished ? '发布设置' : '发布应用'"
    :confirm-loading="submitting"
    destroy-on-close
    width="560px"
    @cancel="emit('update:open', false)"
    @ok="handleSubmit"
  >
    <a-form layout="vertical">
      <a-form-item label="发布版本" required>
        <a-select
          v-model:value="form.versionId"
          placeholder="选择要发布的版本"
          :loading="loadingVersions"
          :options="versionOptions"
        />
      </a-form-item>
      <a-form-item label="公开标题" required>
        <a-input v-model:value="form.publicTitle" maxlength="128" show-count />
      </a-form-item>
      <a-form-item label="公开简介">
        <a-textarea v-model:value="form.publicDescription" :rows="4" maxlength="1024" show-count />
      </a-form-item>
      <a-form-item label="允许在线预览">
        <a-switch v-model:checked="form.allowPreview" />
      </a-form-item>
      <a-form-item label="允许源码下载">
        <a-switch v-model:checked="form.allowDownload" />
        <div v-if="form.allowDownload" class="publish-hint">
          开启下载需要所选版本已有 READY 状态的导出包
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { listAppVersions } from '@/api/appVersion'
import {
  getCurrentPublication,
  publishApp,
  updatePublication,
  type AppPublicationResponse,
} from '@/api/publication'
import type { AiAppDetailResponse } from '@/api/app'
import { stringifyId } from '@/utils/stringifyId'
import { isApiSuccessCode } from '@/utils/apiCode'

const props = defineProps<{
  open: boolean
  app: AiAppDetailResponse | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: [publication: AppPublicationResponse]
}>()

const submitting = ref(false)
const loadingVersions = ref(false)
const publication = ref<AppPublicationResponse | null>(null)
const versionOptions = ref<Array<{ label: string; value: string }>>([])

const form = reactive({
  versionId: '',
  publicTitle: '',
  publicDescription: '',
  allowPreview: true,
  allowDownload: false,
})

const isPublished = computed(() => publication.value?.status === 'PUBLISHED')

const resetForm = () => {
  form.publicTitle = props.app?.name ?? ''
  form.publicDescription = props.app?.description ?? ''
  form.allowPreview = true
  form.allowDownload = false
  form.versionId = props.app?.currentVersionId ? stringifyId(props.app.currentVersionId) ?? '' : ''
}

const loadVersions = async () => {
  if (!props.app?.id) return
  loadingVersions.value = true
  try {
    const response = await listAppVersions(props.app.id, { pageNo: 1, pageSize: 50 })
    const records = response.data.data?.records ?? []
    versionOptions.value = records.map((item) => ({
      label: `v${item.versionNo}`,
      value: stringifyId(item.id) ?? '',
    }))
  } finally {
    loadingVersions.value = false
  }
}

const loadPublication = async () => {
  if (!props.app?.id) return
  try {
    const response = await getCurrentPublication(props.app.id)
    if (isApiSuccessCode(response.data.code) && response.data.data) {
      publication.value = response.data.data
      form.versionId = stringifyId(response.data.data.versionId) ?? form.versionId
      form.publicTitle = response.data.data.publicTitle
      form.publicDescription = response.data.data.publicDescription ?? ''
      form.allowPreview = response.data.data.allowPreview
      form.allowDownload = response.data.data.allowDownload
    }
  } catch {
    publication.value = null
  }
}

watch(
  () => props.open,
  async (visible) => {
    if (!visible || !props.app) return
    resetForm()
    await Promise.all([loadVersions(), loadPublication()])
  },
)

const handleSubmit = async () => {
  if (!props.app?.id) return
  if (!form.versionId) {
    message.warning('请选择发布版本')
    return
  }
  if (!form.publicTitle.trim()) {
    message.warning('请填写公开标题')
    return
  }

  submitting.value = true
  try {
    const body = {
      versionId: form.versionId,
      publicTitle: form.publicTitle.trim(),
      publicDescription: form.publicDescription?.trim() || undefined,
      allowPreview: form.allowPreview,
      allowDownload: form.allowDownload,
    }

    const response = publication.value?.publicationId
      ? await updatePublication(props.app.id, publication.value.publicationId, body)
      : await publishApp(props.app.id, body)

    if (!isApiSuccessCode(response.data.code) || !response.data.data) {
      message.error(response.data.message || '发布失败')
      return
    }

    publication.value = response.data.data
    message.success(publication.value.status === 'PUBLISHED' ? '发布成功' : '发布设置已更新')
    emit('success', response.data.data)
    emit('update:open', false)
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || '发布失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.publish-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--cf-text-tertiary);
}
</style>
