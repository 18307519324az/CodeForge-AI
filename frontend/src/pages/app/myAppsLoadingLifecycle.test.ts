import { describe, expect, it } from 'vitest'
import { resolveAppListPage } from './appListPagination'

describe('MyAppsLoadingLifecycleTest', () => {
  it('stops pagination correction after coercing string page numbers', () => {
    const first = resolveAppListPage(1, 12, {
      pageNo: '1',
      pageSize: '12',
      total: '66',
      records: [{ id: '3001' }],
    })
    expect(first.needsRefetch).toBe(false)

    const second = resolveAppListPage(first.pageNo, first.pageSize, {
      pageNo: String(first.pageNo),
      pageSize: String(first.pageSize),
      total: String(first.total),
      records: [{ id: '3001' }],
    })
    expect(second.needsRefetch).toBe(false)
  })
})

describe('MyAppsLoadingStopsOnSuccessTest', () => {
  it('resolves successful payload without requiring another fetch', () => {
    const resolved = resolveAppListPage(2, 12, {
      pageNo: '2',
      pageSize: '12',
      total: '66',
      records: [{ id: '3013' }],
    })
    expect(resolved.records).toHaveLength(1)
    expect(resolved.needsRefetch).toBe(false)
  })
})

describe('MyAppsLoadingStopsOnFailureTest', () => {
  it('falls back to request page when response page is invalid', () => {
    const resolved = resolveAppListPage(1, 12, {
      pageNo: undefined,
      pageSize: undefined,
      total: undefined,
      records: [],
    })
    expect(resolved.pageNo).toBe(1)
    expect(resolved.pageSize).toBe(12)
    expect(resolved.total).toBe(0)
    expect(resolved.needsRefetch).toBe(false)
  })
})

describe('MyAppsLatestRequestWinsTest', () => {
  it('only marks stale page correction when backend still returns out-of-range page', () => {
    const resolved = resolveAppListPage(99, 12, {
      pageNo: '99',
      pageSize: '12',
      total: '66',
      records: [{ id: '3061' }],
    })
    expect(resolved.pageNo).toBe(6)
    expect(resolved.needsRefetch).toBe(true)

    const corrected = resolveAppListPage(resolved.pageNo, resolved.pageSize, {
      pageNo: '6',
      pageSize: '12',
      total: '66',
      records: [{ id: '3061' }],
    })
    expect(corrected.needsRefetch).toBe(false)
  })
})
