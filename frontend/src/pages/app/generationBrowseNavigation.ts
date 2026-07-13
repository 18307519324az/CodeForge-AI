import type { IdLike, LongId } from '@/types/id'
import { normalizeLongId } from '@/utils/normalizeLongId'

export const MISSING_GENERATION_VERSION_MESSAGE =
  '无法定位生成版本，请稍后重试或重新加载任务详情'

export function resolveBrowseVersionId(options: {
  resolvedVersionId?: LongId | null
  currentVersionId?: LongId | null
  events: Array<{ data?: Record<string, unknown> }>
}): string | null {
  const fromResolved = normalizeLongId(options.resolvedVersionId)
  if (fromResolved) {
    return fromResolved
  }

  const authoritative = normalizeLongId(options.currentVersionId)
  if (authoritative) {
    return authoritative
  }

  for (let index = options.events.length - 1; index >= 0; index -= 1) {
    const eventVersionId = normalizeLongId(options.events[index]?.data?.versionId)
    if (eventVersionId) {
      return eventVersionId
    }
  }

  return null
}

export function buildGeneratedFilesBrowseTarget(
  appId: IdLike,
  versionId: IdLike | null | undefined,
): string | null {
  const normalizedAppId = String(appId ?? '').trim()
  const normalizedVersionId = normalizeLongId(versionId)
  if (!normalizedAppId || !normalizedVersionId) {
    return null
  }
  return `/apps/${normalizedAppId}/files?versionId=${encodeURIComponent(normalizedVersionId)}`
}
