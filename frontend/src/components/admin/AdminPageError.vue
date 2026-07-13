<template>
  <div class="admin-page-error">
    <a-result status="error" :title="title" :sub-title="subTitle">
      <template #extra>
        <AdminRequestIdCopy v-if="error?.requestId" :request-id="error.requestId" />
        <a-space>
          <a-button v-if="showRetry" type="primary" @click="$emit('retry')">
            {{ retryText }}
          </a-button>
          <a-button @click="goHome">{{ homeText }}</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { AdminApiError } from '@/types/adminError'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'
import AdminRequestIdCopy from './AdminRequestIdCopy.vue'

const props = withDefaults(
  defineProps<{
    error?: AdminApiError | null
    title?: string
    subTitle?: string
    showRetry?: boolean
    retryText?: string
    homeText?: string
  }>(),
  {
    title: adminErrorMessages.server,
    showRetry: true,
    retryText: adminErrorMessages.retry,
    homeText: adminErrorMessages.goHome,
  },
)

defineEmits<{ retry: [] }>()

const router = useRouter()

const subTitle = computed(
  () => props.subTitle || props.error?.message || adminErrorMessages.server,
)

function goHome() {
  void router.push('/')
}
</script>
