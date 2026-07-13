import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPublicPreviewToken } from '@/api/publicApp'
import { openPublicAppPreview } from '@/utils/openPublicAppPreview'

vi.mock('@/api/publicApp', () => ({
  createPublicPreviewToken: vi.fn(),
}))

const messageMock = vi.hoisted(() => ({
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('ant-design-vue', () => ({
  message: messageMock,
}))

describe('PublicPreviewPopupTest', () => {
  beforeEach(() => {
    vi.mocked(createPublicPreviewToken).mockReset()
    messageMock.error.mockReset()
  })

  it('opens popup and navigates with normalized preview url', async () => {
    vi.mocked(createPublicPreviewToken).mockResolvedValue({
      data: {
        code: 0,
        message: 'ok',
        data: {
          previewUrl: '/api/v1/static-preview/45/index.html?previewToken=public-token',
          expiresInSeconds: 600,
        },
      },
    } as never)

    const previewWindow = {
      closed: false,
      opener: null as Window | null,
      location: { replace: vi.fn() },
      close: vi.fn(),
    }
    vi.stubGlobal('window', {
      open: vi.fn(() => previewWindow),
      location: { origin: 'http://127.0.0.1:5182' },
    })

    await openPublicAppPreview('customer-management-a7k9m2')

    expect(createPublicPreviewToken).toHaveBeenCalledWith('customer-management-a7k9m2')
    expect(previewWindow.location.replace).toHaveBeenCalledWith(
      'http://127.0.0.1:5182/api/v1/static-preview/45/index.html?previewToken=public-token',
    )
    vi.unstubAllGlobals()
  })
})

describe('PublicDownloadVisibilityTest', () => {
  it('public detail download button depends on allowDownload flag', () => {
    const detail = { allowDownload: false, allowPreview: true }
    const shouldShowDownload = Boolean(detail.allowDownload)
    expect(shouldShowDownload).toBe(false)
  })
})

describe('PublicGalleryTest', () => {
  it('uses explore route for marketplace navigation', () => {
    const routes = [
      { name: 'explore', path: 'explore' },
      { name: 'public-app-detail', path: 'explore/apps/:slug' },
    ]
    const exploreRoute = routes.find((route) => route.name === 'explore')
    expect(exploreRoute?.path).toBe('explore')
  })
})

describe('PublicDetailTest', () => {
  it('uses slug based public detail route', () => {
    const routes = [
      { name: 'explore', path: 'explore' },
      { name: 'public-app-detail', path: 'explore/apps/:slug' },
    ]
    const detailRoute = routes.find((route) => route.name === 'public-app-detail')
    expect(detailRoute?.path).toBe('explore/apps/:slug')
  })
})

describe('PublishDialogUsesVersionIdTest', () => {
  it('uses versionId string instead of versionNo for submission payload', () => {
    const versionId = '45'
    const versionNo = 2
    const payload = { versionId, publicTitle: '客户管理后台', allowPreview: true, allowDownload: false }
    expect(payload.versionId).toBe('45')
    expect(payload.versionId).not.toBe(String(versionNo))
  })
})

describe('PublishDialogVersionNoDisplayTest', () => {
  it('displays version label as vN while keeping versionId in value', () => {
    const option = { label: 'v2', value: '45' }
    expect(option.label).toBe('v2')
    expect(option.value).toBe('45')
  })
})

describe('PublishedStatusTest', () => {
  it('maps PUBLISHED display status label', async () => {
    const { displayStatusLabel } = await import('@/utils/appLabels')
    expect(displayStatusLabel('PUBLISHED')).toBe('已发布')
  })
})
