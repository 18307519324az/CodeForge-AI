import type { AxiosResponse } from 'axios'
import type { PreviewTokenResponse } from '@/api/preview'
import { isApiSuccessCode } from '@/utils/apiCode'

interface ApiEnvelope<T> {
  code?: unknown
  message?: string
  data?: T
}

export function extractPreviewTokenPayload(
  response: AxiosResponse<ApiEnvelope<PreviewTokenResponse>>,
): PreviewTokenResponse | null {
  const envelope = response.data
  if (!isApiSuccessCode(envelope?.code)) {
    return null
  }
  const previewUrl = envelope.data?.previewUrl?.trim()
  if (!previewUrl) {
    return null
  }
  return {
    previewUrl,
    expiresInSeconds: Number(envelope.data?.expiresInSeconds ?? 0),
  }
}
