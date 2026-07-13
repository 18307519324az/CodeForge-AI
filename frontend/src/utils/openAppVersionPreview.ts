import { message } from 'ant-design-vue'
import { createAppVersionPreviewToken } from '@/api/preview'
import type { IdLike } from '@/types/id'
import { previewOpenErrorMessage, resolvePreviewOpenErrorMessage } from '@/utils/previewOpenErrors'
import { extractPreviewTokenPayload } from '@/utils/previewTokenResponse'
import { stringifyId } from '@/utils/stringifyId'

export const resolvePreviewUrl = (rawPreviewUrl: string): string => {
  const trimmed = rawPreviewUrl.trim()
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed
  }
  const path = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  return new URL(path, window.location.origin).toString()
}

type PreviewWindow = Window | null

const openBlankPreviewWindow = (): PreviewWindow => {
  try {
    return window.open('about:blank', '_blank')
  } catch {
    return null
  }
}

const closePreviewWindow = (previewWindow: PreviewWindow) => {
  if (previewWindow && !previewWindow.closed) {
    previewWindow.close()
  }
}

export async function openAppVersionPreview(
  appId: IdLike,
  versionId?: IdLike | null,
): Promise<string | null> {
  const safeAppId = stringifyId(appId)
  const safeVersionId = stringifyId(versionId)
  if (!safeAppId || !safeVersionId) {
    message.warning('当前应用暂无可预览版本')
    return null
  }

  const previewWindow = openBlankPreviewWindow()
  if (!previewWindow) {
    message.error(previewOpenErrorMessage('POPUP_BLOCKED'))
    return null
  }

  try {
    const response = await createAppVersionPreviewToken(safeAppId, safeVersionId)
    const payload = extractPreviewTokenPayload(response)
    if (!payload?.previewUrl) {
      closePreviewWindow(previewWindow)
      message.error(previewOpenErrorMessage('PREVIEW_URL_MISSING'))
      return null
    }

    const previewUrl = resolvePreviewUrl(payload.previewUrl)
    previewWindow.location.replace(previewUrl)
    previewWindow.opener = null
    return previewUrl
  } catch (error) {
    closePreviewWindow(previewWindow)
    message.error(resolvePreviewOpenErrorMessage(error))
    return null
  }
}

/** @deprecated 使用 openAppVersionPreview；保留别名避免大范围重命名。 */
export const openStaticPreview = openAppVersionPreview
