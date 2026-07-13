import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const routerSource = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../../src/router/index.ts'),
  'utf8',
)

describe('admin routes', () => {
  it('maps /admin/apps to AppManagePage', () => {
    expect(routerSource).toMatch(/path: 'admin\/apps'[\s\S]*?AppManagePage\.vue/)
    expect(routerSource).toMatch(/name: 'admin-apps'/)
  })

  it('maps /admin/model-providers to ModelProviderPage', () => {
    expect(routerSource).toMatch(/path: 'admin\/model-providers'[\s\S]*?ModelProviderPage\.vue/)
    expect(routerSource).toMatch(/name: 'admin-model-providers'/)
  })

  it('maps /admin/prompt-templates to PromptTemplateManagePage', () => {
    expect(routerSource).toMatch(/path: 'admin\/prompt-templates'[\s\S]*?PromptTemplateManagePage\.vue/)
    expect(routerSource).toMatch(/name: 'admin-prompt-templates'/)
    expect(routerSource).toMatch(/requiresAdmin: true/)
  })
})
