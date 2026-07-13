import { ref } from 'vue'
import type { FieldError } from '@/types/adminError'
import { AdminApiError } from '@/types/adminError'

export function useAdminFormErrors() {
  const fieldErrorMap = ref<Record<string, string>>({})

  function applyFieldErrors(fieldErrors?: FieldError[]) {
    const next: Record<string, string> = {}
    fieldErrors?.forEach((item) => {
      next[item.field] = item.message
    })
    fieldErrorMap.value = next
  }

  function getFieldError(field: string) {
    return fieldErrorMap.value[field]
  }

  function clearFieldErrors() {
    fieldErrorMap.value = {}
  }

  function handleFormSubmitError(error: unknown) {
    if (error instanceof AdminApiError && error.fieldErrors?.length) {
      applyFieldErrors(error.fieldErrors)
      return true
    }
    return false
  }

  return {
    fieldErrorMap,
    applyFieldErrors,
    getFieldError,
    clearFieldErrors,
    handleFormSubmitError,
  }
}
