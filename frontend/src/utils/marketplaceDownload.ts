export type PublicationDownloadAvailability =
  | 'DISABLED'
  | 'NOT_READY'
  | 'PROCESSING'
  | 'FAILED'
  | 'AVAILABLE'

export interface MarketplaceDownloadStateInput {
  allowDownload?: boolean
  downloadAvailability?: PublicationDownloadAvailability
}

export interface MarketplaceDownloadUiState {
  showAction: boolean
  clickable: boolean
  label: string
}

export function resolveMarketplaceDownloadUi(
  input: MarketplaceDownloadStateInput,
): MarketplaceDownloadUiState {
  const availability = input.downloadAvailability
  if (!input.allowDownload || availability === 'DISABLED') {
    return { showAction: false, clickable: false, label: '' }
  }
  switch (availability) {
    case 'AVAILABLE':
      return { showAction: true, clickable: true, label: '下载源码' }
    case 'PROCESSING':
      return { showAction: true, clickable: false, label: '源码包生成中' }
    case 'FAILED':
      return { showAction: true, clickable: false, label: '源码包不可用' }
    case 'NOT_READY':
    default:
      return { showAction: true, clickable: false, label: '源码包未准备' }
  }
}

export function canTriggerPublicDownload(input: MarketplaceDownloadStateInput): boolean {
  return resolveMarketplaceDownloadUi(input).clickable
}
