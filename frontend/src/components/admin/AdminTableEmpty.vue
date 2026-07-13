<template>
  <AdminEmpty v-if="!filtered" :title="emptyTitle" :description="emptyDescription" />
  <AdminFilteredEmpty
    v-else
    :title="filteredTitle"
    :description="filteredDescription"
    @reset="$emit('reset')"
  />
</template>

<script setup lang="ts">
import AdminEmpty from './AdminEmpty.vue'
import AdminFilteredEmpty from './AdminFilteredEmpty.vue'
import { adminEmptyMessages } from '@/locales/zh-CN/admin/empty'

withDefaults(
  defineProps<{
    filtered?: boolean
    emptyTitle?: string
    emptyDescription?: string
    filteredTitle?: string
    filteredDescription?: string
  }>(),
  {
    filtered: false,
    emptyTitle: adminEmptyMessages.default,
    filteredTitle: adminEmptyMessages.filtered,
    filteredDescription: adminEmptyMessages.filteredHint,
  },
)

defineEmits<{ reset: [] }>()
</script>
