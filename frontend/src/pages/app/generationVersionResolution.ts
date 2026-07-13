import type { LongId } from '@/types/id'
import { normalizeLongId } from '@/utils/normalizeLongId'

export interface VersionResolutionEvent {
  data?: Record<string, unknown>
}

export function resolveGenerationResultVersionId(options: {
  currentVersionId?: LongId | null
  events: VersionResolutionEvent[]
}): string | null {
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
