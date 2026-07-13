import { message } from 'ant-design-vue'
import { createPublicDownloadToken } from '@/api/publicApp'
import { isApiSuccessCode } from '@/utils/apiCode'
import { canTriggerPublicDownload, type MarketplaceDownloadStateInput } from '@/utils/marketplaceDownload'
import { resolvePreviewUrl } from '@/utils/openAppVersionPreview'

export async function triggerPublicAppDownload(
  slug: string,
  state: MarketplaceDownloadStateInput,
): Promise<boolean> {
  if (!slug || !canTriggerPublicDownload(state)) {
    return false
  }
  try {
    const response = await createPublicDownloadToken(slug)
    const payload = response.data.data
    if (!isApiSuccessCode(response.data.code) || !payload?.downloadUrl) {
      message.error(response.data.message || '无法获取下载凭证')
      return false
    }
    window.location.href = resolvePreviewUrl(payload.downloadUrl)
    return true
  } catch (error) {
    const err = error as { response?: { data?: { message?: string } } }
    message.error(err.response?.data?.message || '下载失败，该应用可能未开放源码下载')
    return false
  }
}
