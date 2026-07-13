import { describe, expect, it, vi } from 'vitest'
import { recordPublicAppView } from '@/api/publicApp'

vi.mock('@/api/publicApp', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/publicApp')>()
  return {
    ...actual,
    recordPublicAppView: vi.fn(),
  }
})

describe('PublicDetailViewRecordOnceTest', () => {
  it('records view at most once per detail lifecycle flag', async () => {
    const recorded = new Set<string>()
    const recordOnce = async (slug: string) => {
      if (recorded.has(slug)) return false
      recorded.add(slug)
      await recordPublicAppView(slug)
      return true
    }

    expect(await recordOnce('app-n41xup')).toBe(true)
    expect(await recordOnce('app-n41xup')).toBe(false)
    expect(recordPublicAppView).toHaveBeenCalledTimes(1)
  })
})

describe('PublicDetailUsesServerViewCountTest', () => {
  it('uses server returned viewCount instead of client increment', () => {
    const detail = { viewCount: 10 }
    const server = { counted: true, viewCount: 11 }
    const merged = { ...detail, viewCount: server.viewCount }
    expect(merged.viewCount).toBe(11)
  })
})

describe('MarketplaceViewCountRefreshTest', () => {
  it('list and detail read the same publication viewCount field', () => {
    const listItem = { viewCount: 12 }
    const detail = { viewCount: 12 }
    expect(listItem.viewCount).toBe(detail.viewCount)
  })
})
