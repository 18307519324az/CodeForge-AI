<template>
  <AdminEmpty :title="title" :description="description">
    <template #action>
      <a-button type="primary" @click="$emit('reset')">{{ resetText }}</a-button>
    </template>
  </AdminEmpty>
</template>

<script setup lang="ts">
import AdminEmpty from './AdminEmpty.vue'
import { adminEmptyMessages } from '@/locales/zh-CN/admin/empty'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

withDefaults(
  defineProps<{
    title?: string
    description?: string
    resetText?: string
  }>(),
  {
    title: adminEmptyMessages.filtered,
    description: adminEmptyMessages.filteredHint,
    resetText: adminErrorMessages.resetFilter,
  },
)

defineEmits<{ reset: [] }>()
</script>
