<template>
  <a-alert
    v-if="degradedVisible"
    type="warning"
    banner
    closable
    :message="displayMessage"
    class="admin-degraded-banner"
    @close="store.dismissDegraded()"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAdminUiStore } from '@/stores/adminUi'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

const store = useAdminUiStore()
const { degradedVisible, degradedMessage } = storeToRefs(store)

const displayMessage = computed(
  () => degradedMessage.value || adminErrorMessages.degraded,
)
</script>

<style scoped>
.admin-degraded-banner {
  margin-bottom: 16px;
}
</style>
