<template>
  <a-alert
    type="error"
    show-icon
    :message="message"
    class="admin-partial-failure"
  >
    <template #action>
      <a-button size="small" :loading="loading" @click="$emit('retry')">
        {{ retryText }}
      </a-button>
    </template>
  </a-alert>
</template>

<script setup lang="ts">
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

withDefaults(
  defineProps<{
    message?: string
    loading?: boolean
    retryText?: string
  }>(),
  {
    message: adminErrorMessages.partialFailureDefault,
    loading: false,
    retryText: adminErrorMessages.retry,
  },
)

defineEmits<{ retry: [] }>()
</script>

<style scoped>
.admin-partial-failure {
  margin-bottom: 16px;
}
</style>
