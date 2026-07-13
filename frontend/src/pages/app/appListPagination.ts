export const ALLOWED_PAGE_SIZES = [12, 24, 48] as const
export const DEFAULT_PAGE_SIZE = 12

/** Jackson 将 long 序列化为字符串，列表分页字段需统一转为 number。 */
export function coercePageNumber(value: unknown, fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return fallback
}

export function normalizePageSize(size: number): number {
  return (ALLOWED_PAGE_SIZES as readonly number[]).includes(size) ? size : DEFAULT_PAGE_SIZE
}

export function totalPages(total: number, pageSize: number): number {
  if (total <= 0 || pageSize <= 0) {
    return 0
  }
  return Math.ceil(total / pageSize)
}

export function clampPageNo(pageNo: number, total: number, pageSize: number): number {
  const pages = totalPages(total, pageSize)
  if (pages <= 0) {
    return 1
  }
  return Math.min(Math.max(1, pageNo), pages)
}

export function buildListQueryParams(input: {
  keyword?: string
  status?: string
  appType?: string
  pageNo: number
  pageSize: number
}) {
  return {
    keyword: input.keyword || undefined,
    status: input.status || undefined,
    appType: input.appType || undefined,
    pageNo: input.pageNo,
    pageSize: normalizePageSize(input.pageSize),
  }
}

export interface AppListPagePayload {
  pageNo?: unknown
  pageSize?: unknown
  total?: unknown
  records?: unknown[]
}

export interface ResolvedAppListPage {
  pageNo: number
  pageSize: number
  total: number
  records: unknown[]
  needsRefetch: boolean
}

export function resolveAppListPage(
  requestPageNo: number,
  requestPageSize: number,
  data: AppListPagePayload,
): ResolvedAppListPage {
  const total = coercePageNumber(data.total, 0)
  const pageSize = normalizePageSize(coercePageNumber(data.pageSize, requestPageSize))
  const responsePageNo = coercePageNumber(data.pageNo, requestPageNo)
  const pageNo = clampPageNo(responsePageNo, total, pageSize)
  return {
    pageNo,
    pageSize,
    total,
    records: Array.isArray(data.records) ? data.records : [],
    needsRefetch: pageNo !== responsePageNo && total > 0,
  }
}
