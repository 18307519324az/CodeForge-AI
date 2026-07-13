<template>
  <a-result status="403" :title="title" :sub-title="subTitle">
    <template #extra>
      <a-button type="primary" @click="goHome">{{ homeText }}</a-button>
    </template>
  </a-result>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

withDefaults(
  defineProps<{
    title?: string
    subTitle?: string
    homeText?: string
  }>(),
  {
    title: adminErrorMessages.forbiddenTitle,
    subTitle: adminErrorMessages.forbiddenSub,
    homeText: adminErrorMessages.goHome,
  },
)

const router = useRouter()

function goHome() {
  void router.push('/')
}
</script>

<style scoped>
:deep(.ant-result) {
  padding: 48px 24px;
}
</style>
