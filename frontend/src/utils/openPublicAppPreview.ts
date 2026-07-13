import { message } from 'ant-design-vue'
import { createPublicPreviewToken } from '@/api/publicApp'
import { previewOpenErrorMessage, resolvePreviewOpenErrorMessage } from '@/utils/previewOpenErrors'
import { extractPreviewTokenPayload } from '@/utils/previewTokenResponse'
import { resolvePreviewUrl } from '@/utils/openAppVersionPreview'

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

export async function openPublicAppPreview(slug: string): Promise<string | null> {
  if (!slug) {
    message.warning('应用标识无效')
    return null
  }

  const previewWindow = openBlankPreviewWindow()
  if (!previewWindow) {
    message.error(previewOpenErrorMessage('POPUP_BLOCKED'))
    return null
  }

  try {
    const response = await createPublicPreviewToken(slug)
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
