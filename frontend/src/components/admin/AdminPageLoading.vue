<template>
  <div class="admin-page-loading">
    <a-skeleton active :paragraph="{ rows }" />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ rows?: number }>(), { rows: 6 })
</script>

<style scoped>
.admin-page-loading {
  padding: 24px;
}
</style>
