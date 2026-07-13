import { describe, expect, it } from 'vitest'
import {
  canTriggerPublicDownload,
  resolveMarketplaceDownloadUi,
} from '@/utils/marketplaceDownload'

describe('MarketplaceDownloadButtonVisibilityTest', () => {
  it('hides download action when allowDownload is false', () => {
    const ui = resolveMarketplaceDownloadUi({
      allowDownload: false,
      downloadAvailability: 'DISABLED',
    })
    expect(ui.showAction).toBe(false)
    expect(ui.clickable).toBe(false)
  })

  it('shows clickable download when availability is AVAILABLE', () => {
    const ui = resolveMarketplaceDownloadUi({
      allowDownload: true,
      downloadAvailability: 'AVAILABLE',
    })
    expect(ui.showAction).toBe(true)
    expect(ui.clickable).toBe(true)
    expect(ui.label).toBe('下载源码')
  })

  it('shows disabled processing label', () => {
    const ui = resolveMarketplaceDownloadUi({
      allowDownload: true,
      downloadAvailability: 'PROCESSING',
    })
    expect(ui.showAction).toBe(true)
    expect(ui.clickable).toBe(false)
    expect(ui.label).toBe('源码包生成中')
  })
})

describe('MarketplaceDownloadActionTest', () => {
  it('only allows trigger when availability is AVAILABLE', () => {
    expect(
      canTriggerPublicDownload({ allowDownload: true, downloadAvailability: 'AVAILABLE' }),
    ).toBe(true)
    expect(
      canTriggerPublicDownload({ allowDownload: true, downloadAvailability: 'PROCESSING' }),
    ).toBe(false)
  })
})

describe('PublicDetailDownloadAvailabilityTest', () => {
  it('maps NOT_READY to disabled hint', () => {
    const ui = resolveMarketplaceDownloadUi({
      allowDownload: true,
      downloadAvailability: 'NOT_READY',
    })
    expect(ui.label).toBe('源码包未准备')
    expect(ui.clickable).toBe(false)
  })
})
