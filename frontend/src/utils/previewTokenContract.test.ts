import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => vi.fn())

vi.mock('@/request', () => ({
  default: requestMock,
}))

import { createAppVersionPreviewToken } from '@/api/preview'
import { stringifyId } from '@/utils/stringifyId'

describe('VersionIdVersionNoSeparationTest', () => {
  it('keeps versionId as string and versionNo as number semantics', () => {
    const versionId = '2073999739753553920'
    const versionNo = 45
    expect(stringifyId(versionId)).toBe('2073999739753553920')
    expect(versionNo).toBe(45)
    expect(stringifyId(versionNo)).toBe('45')
  })
})

describe('SnowflakeIdPrecisionTest', () => {
  it('does not lose precision when stringifying snowflake ids', () => {
    const appId = '2073999739753553920'
    expect(String(Number(appId))).not.toBe(appId)
    expect(stringifyId(appId)).toBe(appId)
  })
})

describe('PreviewTokenUsesVersionIdTest', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockResolvedValue({
      data: { code: 0, data: { previewUrl: '/api/v1/static-preview/9001/index.html?previewToken=abc' } },
    })
  })

  it('builds preview path with versionId not versionNo', async () => {
    await createAppVersionPreviewToken('3001', '9001')
    expect(requestMock).toHaveBeenCalledWith('/apps/3001/versions/9001/preview-token', {
      method: 'POST',
      skipGlobalErrorToast: true,
    })
  })

  it('rejects versionNo mistaken as versionId when empty', async () => {
    await expect(createAppVersionPreviewToken('3001', '')).rejects.toThrow('预览参数无效')
  })
})
