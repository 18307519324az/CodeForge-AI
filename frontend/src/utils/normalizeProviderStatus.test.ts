import { describe, expect, it } from 'vitest'
import {
  isProviderEnabled,
  normalizeProviderStatus,
  providerStatusLabel,
} from './normalizeProviderStatus'

describe('normalizeProviderStatus', () => {
  it('maps ACTIVE and ENABLED to enabled state', () => {
    expect(normalizeProviderStatus('ACTIVE')).toBe('ACTIVE')
    expect(normalizeProviderStatus('active')).toBe('ACTIVE')
    expect(normalizeProviderStatus('ENABLED')).toBe('ACTIVE')
    expect(isProviderEnabled('ACTIVE')).toBe(true)
    expect(providerStatusLabel('ACTIVE')).toBe('已启用')
  })

  it('maps DISABLED and INACTIVE to disabled state', () => {
    expect(normalizeProviderStatus('DISABLED')).toBe('DISABLED')
    expect(normalizeProviderStatus('INACTIVE')).toBe('DISABLED')
    expect(isProviderEnabled('DISABLED')).toBe(false)
    expect(providerStatusLabel('INACTIVE')).toBe('已禁用')
  })

  it('treats unknown or empty as disabled', () => {
    expect(normalizeProviderStatus(null)).toBe('DISABLED')
    expect(normalizeProviderStatus('')).toBe('DISABLED')
    expect(normalizeProviderStatus('UNKNOWN')).toBe('DISABLED')
  })
})
