<template>
  <div class="admin-page-shell">
    <slot />
  </div>
</template>

<style scoped>
.admin-page-shell {
  box-sizing: border-box;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 20px 24px 32px;
}
</style>
