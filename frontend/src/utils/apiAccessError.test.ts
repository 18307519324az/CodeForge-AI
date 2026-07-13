import { describe, expect, it } from 'vitest'
import { resolveApiAccessError } from '@/utils/apiAccessError'

describe('resolveApiAccessError', () => {
  it('maps 401 to login recovery', () => {
    const view = resolveApiAccessError({
      response: { status: 401, data: { code: 40100, message: '未登录或登录失效' } },
    })

    expect(view.kind).toBe('unauthorized')
    expect(view.clearToken).toBe(true)
    expect(view.redirectToLogin).toBe(true)
  })

  it('maps 403 without clearing token', () => {
    const view = resolveApiAccessError({
      response: { status: 403, data: { code: 40301, message: '资源访问被拒绝' } },
    })

    expect(view.kind).toBe('forbidden')
    expect(view.clearToken).toBe(false)
    expect(view.redirectToLogin).toBe(false)
  })

  it('maps app not found to 404 view', () => {
    const view = resolveApiAccessError({
      response: { status: 404, data: { code: 40403, message: '应用不存在' } },
    })

    expect(view.kind).toBe('not-found')
    expect(view.message).toContain('应用不存在')
  })

  it('maps server failures to retryable view', () => {
    const view = resolveApiAccessError({
      response: { status: 500, data: { code: 50000, message: '系统内部错误' } },
    })

    expect(view.kind).toBe('server')
    expect(view.title).toContain('服务暂时不可用')
  })
})
