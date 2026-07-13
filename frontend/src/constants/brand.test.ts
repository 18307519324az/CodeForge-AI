import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { CODEFORGE_MASCOT_URL } from './brand'

describe('brand constants', () => {
  it('uses CodeForge mascot path', () => {
    expect(CODEFORGE_MASCOT_URL).toBe('/brand/codeforge-mascot.png')
  })

  it('index.html references CodeForge favicon', () => {
    const indexHtml = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8')
    expect(indexHtml).toContain('/brand/codeforge-mascot.png')
    expect(indexHtml.toLowerCase()).not.toContain('favicon.ico')
  })
})
