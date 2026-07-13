<template>
  <a-result status="404" :title="title" :sub-title="subTitle">
    <template #extra>
      <a-button type="primary" @click="goBack">{{ backText }}</a-button>
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
    backText?: string
  }>(),
  {
    title: adminErrorMessages.notFoundTitle,
    subTitle: adminErrorMessages.notFoundSub,
    backText: '返回上一页',
  },
)

const router = useRouter()

function goBack() {
  router.back()
}
</script>
