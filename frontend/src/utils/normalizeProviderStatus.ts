/** Backend contract: ACTIVE / DISABLED (INACTIVE maps to DISABLED). Legacy UI may send ENABLED. */

export type NormalizedProviderStatus = 'ACTIVE' | 'DISABLED'

export function normalizeProviderStatus(status?: string | null): NormalizedProviderStatus {
  if (!status || !status.trim()) {
    return 'DISABLED'
  }
  const upper = status.trim().toUpperCase()
  if (upper === 'ACTIVE' || upper === 'ENABLED') {
    return 'ACTIVE'
  }
  if (upper === 'DISABLED' || upper === 'INACTIVE') {
    return 'DISABLED'
  }
  return 'DISABLED'
}

export function isProviderEnabled(status?: string | null): boolean {
  return normalizeProviderStatus(status) === 'ACTIVE'
}

export function providerStatusLabel(status?: string | null): string {
  return isProviderEnabled(status) ? '已启用' : '已禁用'
}

export function providerStatusTagColor(status?: string | null): string {
  return isProviderEnabled(status) ? 'green' : 'default'
}
