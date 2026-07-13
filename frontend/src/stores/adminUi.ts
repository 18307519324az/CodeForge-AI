import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAdminUiStore = defineStore('adminUi', () => {
  const degradedVisible = ref(false)
  const degradedMessage = ref('')
  const degradedDismissed = ref(false)

  function showDegraded(message: string) {
    if (degradedDismissed.value) return
    degradedMessage.value = message
    degradedVisible.value = true
  }

  function dismissDegraded() {
    degradedVisible.value = false
    degradedDismissed.value = true
  }

  function resetDegradedSession() {
    degradedDismissed.value = false
    degradedVisible.value = false
    degradedMessage.value = ''
  }

  return {
    degradedVisible,
    degradedMessage,
    showDegraded,
    dismissDegraded,
    resetDegradedSession,
  }
})
