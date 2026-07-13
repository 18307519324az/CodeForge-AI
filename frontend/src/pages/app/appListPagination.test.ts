import { describe, expect, it } from 'vitest'
import {
  ALLOWED_PAGE_SIZES,
  buildListQueryParams,
  clampPageNo,
  coercePageNumber,
  DEFAULT_PAGE_SIZE,
  normalizePageSize,
  resolveAppListPage,
  totalPages,
} from './appListPagination'

describe('appListPagination', () => {
  it('defaults pageSize to 12', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(12)
    expect(normalizePageSize(50)).toBe(12)
    expect(normalizePageSize(99)).toBe(12)
  })

  it('allows 12, 24, 48 page sizes', () => {
    for (const size of ALLOWED_PAGE_SIZES) {
      expect(normalizePageSize(size)).toBe(size)
    }
  })

  it('computes total pages from total count', () => {
    expect(totalPages(66, 12)).toBe(6)
    expect(totalPages(12, 12)).toBe(1)
    expect(totalPages(0, 12)).toBe(0)
  })

  it('clamps pageNo when current page exceeds total pages', () => {
    expect(clampPageNo(5, 66, 12)).toBe(5)
    expect(clampPageNo(10, 66, 12)).toBe(6)
    expect(clampPageNo(1, 0, 12)).toBe(1)
  })

  it('builds list query params with normalized pageSize', () => {
    expect(
      buildListQueryParams({
        keyword: 'crm',
        status: 'DRAFT',
        appType: 'WEB_APP',
        pageNo: 2,
        pageSize: 24,
      }),
    ).toEqual({
      keyword: 'crm',
      status: 'DRAFT',
      appType: 'WEB_APP',
      pageNo: 2,
      pageSize: 24,
    })
  })

  it('omits empty filters from query params', () => {
    expect(
      buildListQueryParams({
        keyword: '',
        pageNo: 1,
        pageSize: 12,
      }),
    ).toEqual({
      keyword: undefined,
      status: undefined,
      appType: undefined,
      pageNo: 1,
      pageSize: 12,
    })
  })

  it('coerces Jackson string longs to numbers', () => {
    expect(coercePageNumber('1', 0)).toBe(1)
    expect(coercePageNumber('66', 0)).toBe(66)
    expect(coercePageNumber('invalid', 3)).toBe(3)
  })

  it('does not refetch when pageNo arrives as string but is valid', () => {
    const resolved = resolveAppListPage(1, 12, {
      pageNo: '1',
      pageSize: '12',
      total: '66',
      records: [{ id: '1' }],
    })
    expect(resolved.pageNo).toBe(1)
    expect(resolved.pageSize).toBe(12)
    expect(resolved.total).toBe(66)
    expect(resolved.needsRefetch).toBe(false)
  })

  it('requests refetch when response page exceeds total pages', () => {
    const resolved = resolveAppListPage(99, 12, {
      pageNo: '99',
      pageSize: '12',
      total: '66',
      records: [],
    })
    expect(resolved.pageNo).toBe(6)
    expect(resolved.needsRefetch).toBe(true)
  })
})
