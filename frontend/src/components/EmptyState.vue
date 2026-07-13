<template>
  <div class="empty-state">
    <div class="empty-state-icon">
      <slot name="icon">□</slot>
    </div>
    <div class="empty-state-title">{{ title }}</div>
    <div v-if="description" class="empty-state-desc">{{ description }}</div>
    <button v-if="actionText" class="empty-state-action" @click="$emit('action')">
      {{ actionText }}
    </button>
    <slot name="extra" />
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; description?: string; actionText?: string }>()
defineEmits<{ action: [] }>()
</script>

<style scoped>
.empty-state { min-height: 280px; border: 1px solid var(--cf-border); border-radius: 14px; background: var(--cf-surface); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: var(--cf-text-secondary); padding: 40px 24px; }
.empty-state-icon { width: 48px; height: 48px; border-radius: 12px; border: 1px solid var(--cf-border); display: flex; align-items: center; justify-content: center; color: var(--cf-text-tertiary); font-size: 18px; }
.empty-state-title { font-size: 15px; font-weight: 600; color: var(--cf-text); }
.empty-state-desc { font-size: 13px; color: var(--cf-text-secondary); text-align: center; max-width: 360px; line-height: 1.5; }
.empty-state-action { height: 36px; padding: 0 16px; border-radius: 9px; border: none; background: var(--cf-primary); color: #fff; font-size: 14px; font-weight: 500; cursor: pointer; }
.empty-state-action:hover { background: var(--cf-primary-hover); }
</style>
