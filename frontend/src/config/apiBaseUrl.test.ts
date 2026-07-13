import { describe, expect, it } from 'vitest'
import { DEFAULT_API_PROXY_TARGET, resolveApiProxyTarget } from '@/config/proxyTarget'
import { DEFAULT_API_BASE_URL, resolveApiBaseUrl, resolveAppBaseUrl } from '@/config/apiBaseUrl'

describe('DefaultApiBaseUsesRelativeProxyPathTest', () => {
  it('uses /api/v1 when VITE_API_BASE_URL is unset', () => {
    expect(resolveApiBaseUrl({})).toBe('/api/v1')
    expect(resolveApiBaseUrl({})).toBe(DEFAULT_API_BASE_URL)
  })

  it('does not fall back to localhost:8123', () => {
    const apiBaseUrl = resolveApiBaseUrl({})
    expect(apiBaseUrl).not.toContain('localhost:8123')
    expect(apiBaseUrl).not.toContain('8123')
    expect(apiBaseUrl.startsWith('/')).toBe(true)
  })
})

describe('ExplicitApiBaseOverrideStillWorksTest', () => {
  it('prefers explicit VITE_API_BASE_URL override', () => {
    expect(resolveApiBaseUrl({ VITE_API_BASE_URL: 'https://example.test/api/v1' }))
      .toBe('https://example.test/api/v1')
  })

  it('trims trailing slashes from explicit override', () => {
    expect(resolveApiBaseUrl({ VITE_API_BASE_URL: 'https://example.test/api/v1/' }))
      .toBe('https://example.test/api/v1')
  })
})

describe('NoDoubleApiPrefixTest', () => {
  it('derives app base as /api from default api base', () => {
    const apiBaseUrl = resolveApiBaseUrl({})
    const appBaseUrl = resolveAppBaseUrl({}, apiBaseUrl)

    expect(apiBaseUrl).toBe('/api/v1')
    expect(appBaseUrl).toBe('/api')
    expect(`${apiBaseUrl}/auth/login`).toBe('/api/v1/auth/login')
    expect(appBaseUrl).not.toBe('/api/api')
  })
})

describe('DevProxyTargetsBackend8150Test', () => {
  it('defaults vite proxy target to backend port 8150', () => {
    expect(resolveApiProxyTarget({})).toBe(DEFAULT_API_PROXY_TARGET)
    expect(resolveApiProxyTarget({})).toBe('http://127.0.0.1:8150')
  })

  it('keeps browser login on /api/v1 without double /api prefix', () => {
    const apiBaseUrl = resolveApiBaseUrl({})
    const proxyTarget = resolveApiProxyTarget({})
    const browserLoginPath = `${apiBaseUrl}/auth/login`
    const backendLoginPath = `${proxyTarget}${browserLoginPath}`

    expect(browserLoginPath).toBe('/api/v1/auth/login')
    expect(backendLoginPath).toBe('http://127.0.0.1:8150/api/v1/auth/login')
    expect(backendLoginPath).not.toContain('/api/api/')
    expect(backendLoginPath).not.toContain(':8150/v1/')
  })
})
