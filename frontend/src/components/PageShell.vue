<template>
  <div class="page-shell">
    <div class="page-shell-inner" :style="{ maxWidth: typeof maxWidth === 'number' ? maxWidth + 'px' : maxWidth || '1180px' }">
      <div class="page-shell-header">
        <div>
          <h1 class="page-shell-title">{{ title }}</h1>
          <p v-if="description" class="page-shell-desc">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="page-shell-actions">
          <slot name="actions" />
        </div>
      </div>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; description?: string; maxWidth?: number | string }>()
</script>

<style scoped>
.page-shell { width: 100%; min-height: 100%; padding: 32px 32px 48px; box-sizing: border-box; }
.page-shell-inner { width: 100%; max-width: 1180px; margin: 0 auto; }
.page-shell-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.page-shell-title { margin: 0; font-size: 24px; line-height: 32px; font-weight: 700; color: var(--cf-text); }
.page-shell-desc { margin: 6px 0 0; font-size: 14px; line-height: 22px; color: var(--cf-text-secondary); }
.page-shell-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
</style>
