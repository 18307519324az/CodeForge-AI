import { expect, test, type Page } from '@playwright/test'

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`)
  }
  return value
}

const USERNAME = () => requireEnv('CODEFORGE_GATE_USERNAME')
const PASSWORD = () => requireEnv('CODEFORGE_GATE_PASSWORD')

async function readAccessToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('codeforge_access_token') ?? '')
  if (!token) {
    throw new Error('Missing access token after login')
  }
  return token
}

test.describe('Admin overview runtime gate', () => {
  test('dashboard metrics reconcile with model_call_log contract', async ({ page, request }) => {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
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
    })

    await page.goto('/user/login')
    await page.locator('input[placeholder="请输入账号"]').fill(USERNAME())
    await page.locator('input[placeholder="请输入密码"]').fill(PASSWORD())
    await page.locator('button[type="submit"]').click()
    await page.waitForURL((url) => !url.pathname.includes('/user/login'), { timeout: 20000 })

    const token = await readAccessToken(page)

    const meResponse = await request.get('/api/v1/users/me', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(meResponse.ok()).toBeTruthy()
    const meBody = await meResponse.json()
    expect(meBody?.data?.platformRoles ?? []).toContain('PLATFORM_ADMIN')

    const summaryBefore = await request.get('/api/v1/admin/metrics/summary', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(summaryBefore.ok()).toBeTruthy()
    const summaryBody = await summaryBefore.json()
    const summary = summaryBody?.data

    expect(summary?.metricScope).toBe('MODEL_CALL_ALL_TIME')
    expect(summary?.dataAsOf).toBeTruthy()
    expect(summary?.generatedAt).toBeTruthy()
    expect(summary?.freshnessStatus).toMatch(/FRESH|STALE|EMPTY/)
    const requestCount = Number(summary?.requestCount ?? 0)
    const successCount = Number(summary?.successCount ?? 0)
    const failedCount = Number(summary?.failedCount ?? 0)
    expect(requestCount).toBe(successCount + failedCount)

    const freshnessBefore = summary.freshnessStatus as string

    if (freshnessBefore === 'STALE') {
      await page.goto('/admin/dashboard')
      await expect(page.getByRole('heading', { name: '管理概览' })).toBeVisible({ timeout: 15000 })
      await expect(page.getByText('聚合数据已过期')).toBeVisible()
    }

    const refreshResponse = await request.post('/api/v1/admin/metrics/refresh', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(refreshResponse.ok()).toBeTruthy()

    await page.goto('/admin/dashboard')
    await expect(page.getByRole('heading', { name: '管理概览' })).toBeVisible({ timeout: 15000 })
    await expect(page.getByText('请求总数').first()).toBeVisible()
    const dashboardRequestText = await page.locator('.stat-card').first().textContent()
    expect(dashboardRequestText ?? '').toContain(String(requestCount))

    const summaryAfterResponse = await request.get('/api/v1/admin/metrics/summary', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(summaryAfterResponse.ok()).toBeTruthy()
    const summaryAfter = (await summaryAfterResponse.json())?.data
    expect(summaryAfter?.freshnessStatus).toBe('FRESH')
    expect(Number(summaryAfter?.requestCount ?? 0)).toBe(requestCount)
    expect(Number(summaryAfter?.successCount ?? 0)).toBe(successCount)
    expect(Number(summaryAfter?.failedCount ?? 0)).toBe(failedCount)

    const auditResponse = await request.get('/api/v1/admin/audit-logs?pageNo=1&pageSize=100', {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(auditResponse.ok()).toBeTruthy()
    const auditRecords = (await auditResponse.json())?.data?.records ?? []
    const hasMetricsRefresh = auditRecords.some(
      (row: { actionType?: string }) => row.actionType === 'METRICS_REFRESH',
    )
    expect(hasMetricsRefresh).toBeTruthy()

    expect(consoleErrors, consoleErrors.join(' | ')).toHaveLength(0)
    expect(pageErrors, pageErrors.join(' | ')).toHaveLength(0)
    expect(http5xx, http5xx.join(' | ')).toHaveLength(0)
  })
})
