import { expect, test, type Page, type Route } from '@playwright/test'

const api = (path: string) => `**/api/v1${path}`

const fulfillJson = async (route: Route, body: unknown) => {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

const mockCurrentUser = async (page: Page, role: 'USER' | 'PLATFORM_ADMIN') => {
  await page.route(api('/users/me'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-user',
      data: {
        id: 2001,
        account: role === 'PLATFORM_ADMIN' ? 'admin' : 'demo',
        displayName: role === 'PLATFORM_ADMIN' ? 'Platform Admin' : 'Demo User',
        platformRoles: [role],
      },
    })
  })
}

const mockHomeApis = async (page: Page) => {
  await page.route(api('/workspaces**'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-workspaces',
      data: {
        records: [
          {
            id: 1001,
            name: 'Workspace A',
            description: 'demo workspace',
            ownerUserId: 2001,
            status: 'ACTIVE',
            planCode: 'FREE',
            memberRole: 'OWNER',
          },
        ],
        pageNo: 1,
        pageSize: 50,
        total: 1,
      },
    })
  })

  await page.route(api('/apps**'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-apps',
      data: {
        records: [
          {
            id: 3001,
            workspaceId: 1001,
            name: 'Starter App',
            description: 'Generated starter app',
            appType: 'WEB_APP',
            status: 'DRAFT',
            visibility: 'PRIVATE',
          },
        ],
        pageNo: 1,
        pageSize: 6,
        total: 1,
      },
    })
  })
}

test('should render login page', async ({ page }) => {
  await page.goto('/user/login')

  await expect(page.locator('#userLoginPage .title')).toContainText('CodeForge AI')
  await expect(page.locator('#userLoginPage input').first()).toBeVisible()
  await expect(page.locator('#userLoginPage input[type="password"]')).toBeVisible()
})

test('should redirect unauthenticated user from admin page to login page', async ({ page }) => {
  await page.goto('/admin/userManage')

  await expect(page).toHaveURL(/\/user\/login\?redirect=\/admin\/userManage/)
  await expect(page.locator('#userLoginPage input').first()).toBeVisible()
})

test('should login and open admin overview with mocked api data', async ({ page }) => {
  await mockCurrentUser(page, 'PLATFORM_ADMIN')
  await mockHomeApis(page)

  await page.route(api('/auth/login'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-login',
      data: {
        accessToken: 'e2e-token',
        tokenType: 'Bearer',
        expiresIn: 7200,
        user: {
          id: 2001,
          account: 'admin',
          displayName: 'Platform Admin',
          platformRoles: ['PLATFORM_ADMIN'],
        },
        platformRoles: ['PLATFORM_ADMIN'],
      },
    })
  })

  await page.route(api('/admin/metrics/summary'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-metrics',
      data: {
        statDate: '2026-06-24',
        requestCount: 12,
        successCount: 10,
        failedCount: 2,
        successRate: '83.33%',
        tokenInput: 1024,
        tokenOutput: 2048,
        avgDurationMs: 300,
      },
    })
  })

  await page.route(api('/admin/quotas/usage-logs'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-quotas',
      data: [
        {
          id: 1,
          taskId: 9001,
          usageType: 'TOKEN',
          requestCount: 1,
          tokenCount: 300,
          costAmount: '0.12',
          createdAt: '2026-06-24T10:00:00',
        },
      ],
    })
  })

  await page.route(api('/admin/users**'), async (route) => {
    await fulfillJson(route, {
      code: 0,
      message: 'ok',
      requestId: 'req-e2e-admin-users',
      data: {
        records: [
          {
            id: 2001,
            account: 'admin',
            displayName: 'Platform Admin',
            email: 'admin@example.com',
            status: 'ACTIVE',
            platformRoles: ['PLATFORM_ADMIN'],
            createdAt: '2026-06-24T10:00:00',
            lastLoginAt: '2026-06-24T11:00:00',
          },
        ],
        pageNo: 1,
        pageSize: 10,
        total: 1,
      },
    })
  })

  await page.goto('/user/login?redirect=/admin/userManage')
  await page.locator('#userLoginPage input').first().fill('admin')
  await page.locator('#userLoginPage input[type="password"]').fill('password123')
  await page.locator('button[type="submit"]').click()

  await expect(page).toHaveURL(/\/admin\/userManage/)
  await expect(page.getByText('Platform Metrics')).toBeVisible()
  await expect(page.getByText('admin@example.com')).toBeVisible()
  await expect(page.getByText('PLATFORM_ADMIN')).toBeVisible()
})
