import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const appChatPageSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), 'AppChatPage.vue'),
  'utf8',
)

describe('FrontendDoesNotRenderStoragePathTest', () => {
  it('does not reference storagePath in AppChatPage', () => {
    expect(appChatPageSource).not.toContain('storagePath')
    expect(appChatPageSource).not.toContain('generated-exports')
  })

  it('shows export package fileName and id in success toast', () => {
    expect(appChatPageSource).toContain('res.data.data.fileName')
    expect(appChatPageSource).toContain('res.data.data.id')
    expect(appChatPageSource).toMatch(/导出包已创建/)
  })

  it('lists export packages by fileName instead of internal path', () => {
    expect(appChatPageSource).toContain('item.fileName')
    expect(appChatPageSource).not.toContain('item.storagePath')
  })
})
