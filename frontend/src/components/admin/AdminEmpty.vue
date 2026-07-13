<template>
  <a-empty :description="title">
    <template v-if="description" #description>
      <div class="admin-empty">
        <p class="admin-empty-title">{{ title }}</p>
        <p v-if="description" class="admin-empty-desc">{{ description }}</p>
      </div>
    </template>
    <template v-if="$slots.action" #extra>
      <slot name="action" />
    </template>
  </a-empty>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '暂无数据',
  },
)
</script>

<style scoped>
.admin-empty-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--cf-text, #1f2937);
}
.admin-empty-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--cf-text-secondary, #6b7280);
}
</style>
