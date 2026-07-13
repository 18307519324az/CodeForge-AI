<template>
  <a-drawer
    :open="open"
    :title="detail?.templateName || '模板详情'"
    width="520"
    @close="emit('close')"
  >
    <a-spin :spinning="loading">
      <template v-if="detail">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="模板名称">{{ detail.templateName }}</a-descriptions-item>
          <a-descriptions-item label="分类">{{ detail.templateSceneLabel }}</a-descriptions-item>
          <a-descriptions-item label="适用类型">{{ detail.applicableAppType }}</a-descriptions-item>
          <a-descriptions-item label="当前发布版本">v{{ detail.publishedVersion.versionNo }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ detail.description || '-' }}</a-descriptions-item>
          <a-descriptions-item label="示例需求">{{ detail.exampleRequirement || '-' }}</a-descriptions-item>
        </a-descriptions>

        <div v-if="detail.variables.length" class="variable-block">
          <h4>变量说明</h4>
          <div v-for="item in detail.variables" :key="item.key" class="variable-item">
            <strong>{{ item.key }}</strong>
            <span>{{ item.required ? '必填' : '可选' }}</span>
            <span>{{ item.type }}</span>
            <p v-if="item.description">{{ item.description }}</p>
          </div>
        </div>
      </template>
      <a-empty v-else-if="!loading" description="未找到模板详情" />
    </a-spin>

    <template #footer>
      <a-space>
        <a-button @click="emit('close')">关闭</a-button>
        <a-button type="primary" :disabled="!detail" @click="emit('use', detail!)">使用模板</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import type { PromptTemplateUserDetail } from '@/utils/promptTemplateTypes'

defineProps<{
  open: boolean
  loading: boolean
  detail: PromptTemplateUserDetail | null
}>()

const emit = defineEmits<{
  close: []
  use: [detail: PromptTemplateUserDetail]
}>()
</script>

<style scoped>
.variable-block { margin-top: 16px; }
.variable-block h4 { margin: 0 0 8px; font-size: 14px; }
.variable-item { border: 1px solid var(--cf-border); border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; display: grid; gap: 4px; }
.variable-item p { margin: 0; color: var(--cf-text-secondary); font-size: 12px; }
</style>
