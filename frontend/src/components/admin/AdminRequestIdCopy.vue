<template>
  <div class="admin-request-id">
    <span class="label">请求 ID：</span>
    <code>{{ requestId }}</code>
    <a-button type="link" size="small" @click="copyId">{{ copyText }}</a-button>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

const props = defineProps<{ requestId: string; copyText?: string }>()

async function copyId() {
  try {
    await navigator.clipboard.writeText(props.requestId)
    message.success(adminErrorMessages.copiedRequestId)
  } catch {
    message.error(adminErrorMessages.copyFailed)
  }
}
</script>

<style scoped>
.admin-request-id {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
  font-size: 12px;
  color: var(--cf-text-secondary, #6b7280);
}
.admin-request-id code {
  font-family: ui-monospace, monospace;
}
</style>
