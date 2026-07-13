import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppVersionPreviewToken } from '@/api/preview'
import { openAppVersionPreview } from '@/utils/openAppVersionPreview'

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
  close: vi.fn(),
})

describe('AppCardPreviewUsesVersionIdTest', () => {
  beforeEach(() => {
    vi.mocked(createAppVersionPreviewToken).mockReset()
    messageMock.error.mockReset()
  })

  it('requests preview token with versionId not versionNo', async () => {
    vi.mocked(createAppVersionPreviewToken).mockResolvedValue({
      data: {
        code: 0,
        message: 'ok',
        data: {
          previewUrl: '/api/v1/static-preview/45/index.html?previewToken=abc',
          expiresInSeconds: 600,
        },
      },
    } as never)
    const previewWindow = buildPreviewWindow()
    const openMock = vi.fn(() => previewWindow as never)
    vi.stubGlobal('window', {
      open: openMock,
      location: { origin: 'http://127.0.0.1:5182' },
    })

    await openAppVersionPreview('2073999739753553920', '45')

    expect(createAppVersionPreviewToken).toHaveBeenCalledWith('2073999739753553920', '45')
    expect(createAppVersionPreviewToken).not.toHaveBeenCalledWith('2073999739753553920', '2')
    expect(previewWindow.location.replace).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})

describe('AppCardPreviewTest', () => {
  beforeEach(() => {
    vi.mocked(createAppVersionPreviewToken).mockReset()
    messageMock.warning.mockReset()
    messageMock.error.mockReset()
  })

  it('warns when current version is missing', async () => {
    const openMock = vi.fn()
    vi.stubGlobal('window', { open: openMock })

    const url = await openAppVersionPreview('3001', null)

    expect(url).toBeNull()
    expect(messageMock.warning).toHaveBeenCalledWith('当前应用暂无可预览版本')
    expect(createAppVersionPreviewToken).not.toHaveBeenCalled()
    expect(openMock).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('requests preview token for current version', async () => {
    vi.mocked(createAppVersionPreviewToken).mockResolvedValue({
      data: {
        code: 0,
        message: 'ok',
        data: {
          previewUrl: '/api/v1/static-preview/9001/index.html?previewToken=abc',
          expiresInSeconds: 600,
        },
      },
    } as never)
    const previewWindow = buildPreviewWindow()
    const openMock = vi.fn(() => previewWindow as never)
    vi.stubGlobal('window', {
      open: openMock,
      location: { origin: 'http://127.0.0.1:5182' },
    })

    await openAppVersionPreview('3001', '9001')

    expect(createAppVersionPreviewToken).toHaveBeenCalledWith('3001', '9001')
    expect(openMock).toHaveBeenCalledWith('about:blank', '_blank')
    expect(previewWindow.location.replace).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
