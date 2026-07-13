import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(__dirname, '../../../frontend')

function loadPlaywright() {
  const require = createRequire(path.join(frontendRoot, 'package.json'))
  return require('@playwright/test')
}

function isBrowserUnavailableError(error) {
  const message = String(error?.message || error)
  return message.includes("Executable doesn't exist")
    || message.includes('browserType.launch')
    || message.includes('Cannot find module')
    || message.includes('BROWSER_RUNTIME_UNAVAILABLE')
}

export async function runBrowserGate({ appId, appVersionId, account, password, origin }) {
  let chromium
  try {
    ;({ chromium } = loadPlaywright())
  } catch (error) {
    return {
      skipped: true,
      launchError: 'BROWSER_RUNTIME_UNAVAILABLE',
      unavailable: true,
      consoleErrors: 0,
      pageErrors: 0,
      http5xx: 0,
      http5xxEvents: [],
    }
  }

  const consoleErrors = []
  const pageErrors = []
  const http5xxEvents = []

  try {
    const browser = await chromium.launch({ headless: true })
    const page = await browser.newPage()

    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    })
    page.on('pageerror', (err) => pageErrors.push(String(err)))
    page.on('response', (response) => {
      const status = response.status()
      if (status >= 500) {
        http5xxEvents.push({
          method: response.request().method(),
          status,
          path: response.url(),
        })
      }
    })

    await page.goto(`${origin}/user/login`, { waitUntil: 'domcontentloaded' })
    await page.locator('input[placeholder="请输入账号"]').fill(account)
    await page.locator('input[placeholder="请输入密码"]').fill(password)
    await page.locator('button[type="submit"]').click()
    await page.waitForTimeout(2500)

    const route = `/apps/${appId}/files?versionId=${encodeURIComponent(String(appVersionId))}`
    await page.goto(`${origin}${route}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(2000)

    const fileTreeVisible = (await page.locator('.file-row').count()) > 0
    const indexRow = page.locator('.file-row', { hasText: 'index.html' })
    const indexVisible = (await indexRow.count()) > 0
    if (indexVisible) {
      await indexRow.first().click()
      await page.waitForTimeout(1500)
    }
    const indexContentVisible = (await page.locator('pre.code-preview code').count()) > 0
      && ((await page.locator('pre.code-preview code').first().innerText()).trim().length > 0)

    await page.reload({ waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    const refreshTreeVisible = (await page.locator('.file-row').count()) > 0
    const refreshIndexVisible = (await page.locator('.file-row', { hasText: 'index.html' }).count()) > 0

    await browser.close()

    return {
      skipped: false,
      route,
      fileTreeVisible,
      indexVisible,
      indexContentVisible,
      refreshPass: refreshTreeVisible && refreshIndexVisible,
      consoleErrors: consoleErrors.length,
      pageErrors: pageErrors.length,
      http5xx: http5xxEvents.length,
      http5xxEvents,
    }
  } catch (error) {
    if (isBrowserUnavailableError(error)) {
      return {
        skipped: true,
        launchError: 'BROWSER_RUNTIME_UNAVAILABLE',
        unavailable: true,
        consoleErrors: consoleErrors.length,
        pageErrors: pageErrors.length,
        http5xx: http5xxEvents.length,
        http5xxEvents,
      }
    }
    return {
      skipped: true,
      launchError: 'BROWSER_LAUNCH_FAILED',
      consoleErrors: consoleErrors.length,
      pageErrors: pageErrors.length,
      http5xx: http5xxEvents.length,
      http5xxEvents,
    }
  }
}
