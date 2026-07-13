export type PreviewOpenErrorCode =
  | 'TOKEN_REQUEST_FAILED'
  | 'PREVIEW_URL_MISSING'
  | 'POPUP_BLOCKED'
  | 'STATIC_PREVIEW_UNAUTHORIZED'
  | 'STATIC_PREVIEW_NOT_FOUND'
  | 'NETWORK_ERROR'

const PREVIEW_ERROR_MESSAGES: Record<PreviewOpenErrorCode, string> = {
  TOKEN_REQUEST_FAILED: '预览凭证获取失败，请重试',
  PREVIEW_URL_MISSING: '预览地址生成失败',
  POPUP_BLOCKED: '浏览器阻止了预览窗口，请允许本站弹出窗口',
  STATIC_PREVIEW_UNAUTHORIZED: '预览授权已失效，请重新打开',
  STATIC_PREVIEW_NOT_FOUND: '预览文件不存在，请重新生成应用',
  NETWORK_ERROR: '网络异常，请稍后重试',
}

export function previewOpenErrorMessage(code: PreviewOpenErrorCode): string {
  return PREVIEW_ERROR_MESSAGES[code]
}

export function resolvePreviewOpenErrorMessage(error: unknown): string {
  const status = (error as { response?: { status?: number } })?.response?.status
  if (status === 401) {
    return previewOpenErrorMessage('STATIC_PREVIEW_UNAUTHORIZED')
  }
  if (status === 403) {
    return previewOpenErrorMessage('STATIC_PREVIEW_UNAUTHORIZED')
  }
  if (status === 404) {
    return previewOpenErrorMessage('STATIC_PREVIEW_NOT_FOUND')
  }
  const msg = error instanceof Error ? error.message : ''
  if (msg.includes('Invalid base URL') || msg.includes('Failed to construct')) {
    return previewOpenErrorMessage('PREVIEW_URL_MISSING')
  }
  if (!status) {
    const msg = error instanceof Error ? error.message : ''
    if (msg.includes('Network Error') || msg.includes('网络')) {
      return previewOpenErrorMessage('NETWORK_ERROR')
    }
  }
  return previewOpenErrorMessage('TOKEN_REQUEST_FAILED')
}
