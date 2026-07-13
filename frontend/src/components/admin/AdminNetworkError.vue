<template>
  <AdminPageError
    :error="error"
    :title="title"
    :sub-title="subTitle"
    @retry="$emit('retry')"
  />
</template>

<script setup lang="ts">
import type { AdminApiError } from '@/types/adminError'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'
import AdminPageError from './AdminPageError.vue'

withDefaults(
  defineProps<{
    error?: AdminApiError | null
    title?: string
    subTitle?: string
  }>(),
  {
    title: adminErrorMessages.network,
    subTitle: adminErrorMessages.network,
  },
)

defineEmits<{ retry: [] }>()
</script>
