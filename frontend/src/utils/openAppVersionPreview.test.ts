import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosResponse } from 'axios'
import { createAppVersionPreviewToken } from '@/api/preview'
import { openAppVersionPreview, resolvePreviewUrl } from '@/utils/openAppVersionPreview'
import { previewOpenErrorMessage } from '@/utils/previewOpenErrors'
import { extractPreviewTokenPayload } from '@/utils/previewTokenResponse'
import { openStaticPreview } from '@/utils/openStaticPreview'

vi.mock('@/api/preview', () => ({
  createAppVersionPreviewToken: vi.fn(),
}))

const messageMock = vi.hoisted(() => ({
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('ant-design-vue', () => ({
  message: messageMock,
}))

const buildPreviewWindow = () => ({
  closed: false,
  opener: null as Window | null,
  location: { replace: vi.fn() },
  close: vi.fn(function (this: { closed: boolean }) {
    this.closed = true
  }),
})

const buildTokenResponse = (): AxiosResponse => ({
  data: {
    code: 0,
    message: 'ok',
    data: {
      previewUrl: '/api/v1/static-preview/45/index.html?previewToken=abc',
      expiresInSeconds: 600,
    },
  },
  status: 200,
  statusText: 'OK',
  headers: {},
  config: { headers: {} } as never,
})

describe('PreviewUrlNormalizationTest', () => {
  it('resolves relative previewUrl against window.location.origin', () => {
    vi.stubGlobal('window', { location: { origin: 'http://127.0.0.1:5182' } })
    const resolved = resolvePreviewUrl('/api/v1/static-preview/45/index.html?previewToken=abc')
    expect(resolved).toBe('http://127.0.0.1:5182/api/v1/static-preview/45/index.html?previewToken=abc')
    vi.unstubAllGlobals()
  })

  it('keeps absolute previewUrl unchanged', () => {
    const absolute = 'http://127.0.0.1:8150/api/v1/static-preview/45/index.html?previewToken=abc'
    expect(resolvePreviewUrl(absolute)).toBe(absolute)
  })
})

describe('PreviewTokenResponseContractTest', () => {
  it('reads previewUrl from axios envelope data.data.previewUrl', () => {
    const payload = extractPreviewTokenPayload(buildTokenResponse())
    expect(payload?.previewUrl).toContain('/api/v1/static-preview/45/index.html')
    expect(payload?.expiresInSeconds).toBe(600)
  })

  it('returns null when previewUrl is missing', () => {
    const response = buildTokenResponse()
    response.data.data = { expiresInSeconds: 600, previewUrl: '' }
    expect(extractPreviewTokenPayload(response)).toBeNull()
  })
})

describe('PreviewOpensWindowBeforeAwaitTest', () => {
  beforeEach(() => {
    vi.mocked(createAppVersionPreviewToken).mockReset()
    messageMock.error.mockReset()
    messageMock.warning.mockReset()
  })

  it('opens blank window before awaiting preview token', async () => {
    let openedBeforeToken = false
    let tokenResolved = false
    const previewWindow = buildPreviewWindow()

    vi.mocked(createAppVersionPreviewToken).mockImplementation(async () => {
      openedBeforeToken = !tokenResolved
      tokenResolved = true
      return buildTokenResponse()
    })

    const openMock = vi.fn(() => {
      if (!tokenResolved) {
        openedBeforeToken = true
      }
      return previewWindow as never
    })
    vi.stubGlobal('window', {
      open: openMock,
      location: { origin: 'http://127.0.0.1:5182' },
    })

    await openAppVersionPreview('2073999739753553920', '45')

    expect(openedBeforeToken).toBe(true)
    expect(openMock).toHaveBeenCalledWith('about:blank', '_blank')
    expect(previewWindow.location.replace).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})

describe('PreviewPopupBlockedMessageTest', () => {
  beforeEach(() => {
    vi.mocked(createAppVersionPreviewToken).mockReset()
    messageMock.error.mockReset()
  })

  it('shows popup blocked message when window.open returns null', async () => {
    vi.stubGlobal('window', { open: vi.fn(() => null) })

    await openAppVersionPreview('2073999739753553920', '45')

    expect(messageMock.error).toHaveBeenCalledWith(previewOpenErrorMessage('POPUP_BLOCKED'))
    expect(createAppVersionPreviewToken).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})

describe('PreviewTokenFailureClosesBlankWindowTest', () => {
  beforeEach(() => {
    vi.mocked(createAppVersionPreviewToken).mockReset()
    messageMock.error.mockReset()
  })

  it('closes blank tab when token request fails', async () => {
    const previewWindow = buildPreviewWindow()
    vi.stubGlobal('window', {
      open: vi.fn(() => previewWindow as never),
      location: { origin: 'http://127.0.0.1:5182' },
    })
    vi.mocked(createAppVersionPreviewToken).mockRejectedValue(new Error('请求失败'))

    await openAppVersionPreview('2073999739753553920', '45')

    expect(previewWindow.close).toHaveBeenCalled()
    expect(messageMock.error).toHaveBeenCalledWith(previewOpenErrorMessage('TOKEN_REQUEST_FAILED'))
    vi.unstubAllGlobals()
  })

  it('closes blank tab when previewUrl is missing', async () => {
    const previewWindow = buildPreviewWindow()
    vi.stubGlobal('window', {
      open: vi.fn(() => previewWindow as never),
      location: { origin: 'http://127.0.0.1:5182' },
    })
    const response = buildTokenResponse()
    response.data.data = { expiresInSeconds: 600, previewUrl: '' }
    vi.mocked(createAppVersionPreviewToken).mockResolvedValue(response)

    await openAppVersionPreview('2073999739753553920', '45')

    expect(previewWindow.close).toHaveBeenCalled()
    expect(messageMock.error).toHaveBeenCalledWith(previewOpenErrorMessage('PREVIEW_URL_MISSING'))
    vi.unstubAllGlobals()
  })
})

describe('AppListAndDetailUseSamePreviewHelperTest', () => {
  it('re-exports the same preview helper for legacy imports', () => {
    expect(openStaticPreview).toBe(openAppVersionPreview)
  })
})
