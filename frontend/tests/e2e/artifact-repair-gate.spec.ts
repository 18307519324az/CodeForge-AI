import { expect, test } from '@playwright/test'

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return value
}

const APP_ID = () => requireEnv('CODEFORGE_REPAIR_APP_ID')
const PASSWORD = () => requireEnv('CODEFORGE_GATE_PASSWORD')
const USERNAME = () => requireEnv('CODEFORGE_GATE_USERNAME')

test.describe('Artifact repair browser gate', () => {
  test('owner can preview repaired artifact without legacy assets', async ({ page, request }) => {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
    const legacyRequests: string[] = []
    const http5xx: string[] = []

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text())
      }
    })
    page.on('pageerror', (error) => pageErrors.push(error.message))
    page.on('response', (response) => {
      if (response.status() >= 500) {
        http5xx.push(`${response.status()} ${response.url()}`)
      }
      const url = response.url().toLowerCase()
      if (url.includes('aiavatar') || url.includes('yupi') || url.includes('/favicon.ico')) {
        legacyRequests.push(response.url())
      }
    })

    await page.goto('/user/login')
    await page.locator('input[placeholder="请输入账号"]').fill(USERNAME())
    await page.locator('input[placeholder="请输入密码"]').fill(PASSWORD())
    await page.locator('button[type="submit"]').click()
    await page.waitForURL((url) => !url.pathname.includes('/user/login'), { timeout: 20000 })

    const mascotResponse = await request.get('/brand/codeforge-mascot.png')
    expect(mascotResponse.ok()).toBeTruthy()

    await page.goto(`/apps/${APP_ID()}`)
    const favicon = await page.locator('link[rel="icon"]').getAttribute('href')
    expect(favicon || '').toContain('codeforge-mascot')

    await page.getByRole('button', { name: '浏览生成文件' }).click()
    await expect(page.getByText('index.html').first()).toBeVisible({ timeout: 15000 })
    await page.getByText('index.html').first().click()

    const previewButton = page.getByRole('button', { name: /预览/ }).first()
    if (await previewButton.count()) {
      await previewButton.click()
      await page.waitForTimeout(1500)
    }

    await page.reload()
    await expect(page).not.toHaveURL(/\/user\/login/)

    expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0)
    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0)
    expect(http5xx, `5xx: ${http5xx.join(' | ')}`).toHaveLength(0)
    expect(legacyRequests, `legacy assets: ${legacyRequests.join(' | ')}`).toHaveLength(0)
  })
})
