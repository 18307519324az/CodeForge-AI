import { describe, expect, it } from 'vitest'
import { DEFAULT_API_PROXY_TARGET, resolveApiProxyTarget } from '@/config/proxyTarget'

describe('resolveApiProxyTarget', () => {
  it('defaults to current backend port 8150', () => {
    expect(resolveApiProxyTarget({})).toBe(DEFAULT_API_PROXY_TARGET)
  })

  it('uses configured proxy target without trailing slash', () => {
    expect(resolveApiProxyTarget({ VITE_API_PROXY_TARGET: 'http://127.0.0.1:8150/' }))
      .toBe('http://127.0.0.1:8150')
  })
})
