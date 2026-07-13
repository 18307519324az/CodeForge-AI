import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return value
}

const APP_ID = () => requireEnv('CODEFORGE_REPAIR_APP_ID')
const SOURCE_VERSION_ID = () => requireEnv('CODEFORGE_REPAIR_SOURCE_VERSION_ID')
const USERNAME = () => requireEnv('CODEFORGE_GATE_USERNAME')
const PASSWORD = () => requireEnv('CODEFORGE_GATE_PASSWORD')

async function readAccessToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('codeforge_access_token') ?? '')
  if (!token) {
    throw new Error('Missing access token after login')
  }
  return token
}

async function fetchModelCallLogTotal(request: APIRequestContext, token: string): Promise<number> {
  const meResponse = await request.get('/api/v1/users/me', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(meResponse.ok(), `users/me status=${meResponse.status()}`).toBeTruthy()
  const meBody = await meResponse.json()
  const roles: string[] = meBody?.data?.platformRoles ?? []
  expect(
    roles.includes('PLATFORM_ADMIN'),
    'CODEFORGE_GATE_USERNAME must include PLATFORM_ADMIN for model_call_log isolation gate',
  ).toBeTruthy()

  const response = await request.get('/api/v1/admin/model-call-logs?pageNo=1&pageSize=1', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok(), `model-call-logs status=${response.status()}`).toBeTruthy()
  const body = await response.json()
  return Number(body?.data?.total ?? 0)
}

async function fetchLatestTaskId(
  request: APIRequestContext,
  token: string,
  appId: string,
): Promise<string | null> {
  const response = await request.get(`/api/v1/apps/${appId}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok(), `apps/${appId} status=${response.status()}`).toBeTruthy()
  const body = await response.json()
  const latestTaskId = body?.data?.latestTaskId
  return latestTaskId == null ? null : String(latestTaskId)
}

test.describe('Artifact repair runtime gate', () => {
  test('owner repairs artifact through real API and preview stays healthy', async ({ page, request }) => {
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

    const token = await readAccessToken(page)
    const appId = APP_ID()
    const sourceVersionId = SOURCE_VERSION_ID()

    const modelCallsBefore = await fetchModelCallLogTotal(request, token)
    const latestTaskIdBefore = await fetchLatestTaskId(request, token, appId)

    const appDetailResponse = await request.get(`/api/v1/apps/${appId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(appDetailResponse.ok()).toBeTruthy()
    const appDetail = await appDetailResponse.json()
    const currentVersionBefore = appDetail?.data?.currentVersionId

    const repairResponse = await request.post(
      `/api/v1/apps/${appId}/versions/${sourceVersionId}/repair`,
      { headers: { Authorization: `Bearer ${token}` } },
    )
    expect(repairResponse.status(), 'repair API must return 200').toBe(200)
    const repairBody = await repairResponse.json()
    const repairedVersionId = repairBody?.data?.repairedVersionId
    expect(repairedVersionId).toBeTruthy()
    expect(repairBody?.data?.versionSource).toBe('MANUAL_REPAIR')
    expect(repairBody?.data?.sourceVersionId).toBe(String(sourceVersionId))

    const appAfterRepair = await request.get(`/api/v1/apps/${appId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(appAfterRepair.ok()).toBeTruthy()
    const appAfterBody = await appAfterRepair.json()
    expect(appAfterBody?.data?.currentVersionId).toBe(repairedVersionId)
    expect(currentVersionBefore).not.toBe(repairedVersionId)

    await page.goto(`/apps/${appId}/files?versionId=${repairedVersionId}`)
    await expect(page.getByText('index.html').first()).toBeVisible({ timeout: 15000 })
    await page.getByText('index.html').first().click()

    const previewButton = page.getByRole('button', { name: /预览/ }).first()
    if (await previewButton.count()) {
      await previewButton.click()
      await page.waitForTimeout(1500)
    }

    const mascotResponse = await request.get('/brand/codeforge-mascot.png')
    expect(mascotResponse.ok()).toBeTruthy()

    const fileContentResponse = await request.get(
      `/api/v1/apps/${appId}/versions/${repairedVersionId}/files/content?filePath=index.html`,
      { headers: { Authorization: `Bearer ${token}` } },
    )
    expect(fileContentResponse.ok()).toBeTruthy()
    const fileContentBody = await fileContentResponse.json()
    const indexContent = String(fileContentBody?.data?.content ?? '')
    expect(indexContent).not.toContain('\\n')

    await page.reload()
    await expect(page).not.toHaveURL(/\/user\/login/)
    await expect(page.getByText('index.html').first()).toBeVisible({ timeout: 15000 })

    const modelCallsAfter = await fetchModelCallLogTotal(request, token)
    const latestTaskIdAfter = await fetchLatestTaskId(request, token, appId)
    expect(modelCallsAfter - modelCallsBefore, 'model_call_log delta must be 0').toBe(0)
    expect(latestTaskIdAfter, 'repair must not create a new generation task').toBe(latestTaskIdBefore)

    expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0)
    expect(pageErrors, `page errors: ${pageErrors.join(' | ')}`).toHaveLength(0)
    expect(http5xx, `5xx: ${http5xx.join(' | ')}`).toHaveLength(0)
    expect(legacyRequests, `legacy assets: ${legacyRequests.join(' | ')}`).toHaveLength(0)
  })
})
