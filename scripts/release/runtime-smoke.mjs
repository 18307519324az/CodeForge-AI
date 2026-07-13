import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'

const baseUrl = (process.env.CODEFORGE_SMOKE_BASE_URL || 'http://127.0.0.1:8150/api/v1').replace(/\/$/, '')

function requiredEnv(name) {
  const value = process.env[name]
  if (!value || !value.trim()) {
    throw new Error(`missing required env ${name}`)
  }
  return value.trim()
}

function requireEqual(actual, expected, label) {
  if (actual !== expected) {
    throw new Error(`${label}: expected=${expected} actual=${actual}`)
  }
}

async function api(token, route, options = {}) {
  const response = await fetch(`${baseUrl}${route}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  })
  const json = await response.json().catch(() => ({}))
  if (!response.ok || json.code !== 0) {
    throw new Error(`api failed ${route} status=${response.status}`)
  }
  return json.data
}

async function apiRaw(token, route, options = {}) {
  const response = await fetch(`${baseUrl}${route}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  })
  const json = await response.json().catch(() => ({}))
  if (!response.ok || json.code !== 0) {
    throw new Error(`api failed ${route} status=${response.status}`)
  }
  return { response, data: json.data }
}

async function register(account, password, displayName) {
  await api('', '/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      account,
      password,
      confirmPassword: password,
      displayName,
      email: `${account}-${Date.now()}@example.invalid`,
    }),
  })
}

async function login(account, password) {
  const data = await api('', '/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password }),
  })
  assert.ok(data.accessToken)
  return data.accessToken
}

function mysqlScalar(sql) {
  const env = {
    ...process.env,
    MYSQL_PWD: requiredEnv('DB_PASSWORD'),
  }
  return execFileSync(
    'mysql',
    [
      '-h',
      requiredEnv('DB_HOST'),
      '-P',
      requiredEnv('DB_PORT'),
      '-u',
      requiredEnv('DB_USERNAME'),
      '-N',
      '-B',
      '-D',
      requiredEnv('DB_NAME'),
      '-e',
      sql,
    ],
    { encoding: 'utf8', env },
  ).trim()
}

async function waitForHealth() {
  const healthUrl = `${baseUrl.replace(/\/api\/v1$/, '/api')}/actuator/health`
  const deadline = Date.now() + 120_000
  while (Date.now() < deadline) {
    const response = await fetch(healthUrl).catch(() => null)
    if (response?.ok) {
      const json = await response.json().catch(() => ({}))
      if (json.status === 'UP') {
        return
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 2000))
  }
  throw new Error('backend health did not become UP')
}

function eventType(event) {
  return event?.type || event?.eventType
}

function eventPayload(event) {
  if (event?.data && typeof event.data === 'object') {
    return event.data
  }
  if (event?.eventPayloadJson) {
    return JSON.parse(event.eventPayloadJson)
  }
  return {}
}

async function main() {
  await waitForHealth()
  const suffix = Date.now()
  const adminAccount = `smoke_admin_${suffix}`
  const userAccount = `smoke_user_${suffix}`
  const password = 'SmokeUser_123456789!'

  await register(adminAccount, password, 'Smoke Admin')
  const adminToken = await login(adminAccount, password)
  const me = await api(adminToken, '/users/me')
  assert.ok((me.platformRoles || []).includes('PLATFORM_ADMIN'))

  await register(userAccount, password, 'Smoke User A')
  const userToken = await login(userAccount, password)
  const workspace = await api(userToken, '/workspaces/default', { method: 'POST' })
  const app = await api(userToken, '/apps', {
    method: 'POST',
    body: JSON.stringify({
      workspaceId: workspace.id,
      name: `Runtime Smoke ${suffix}`,
      description: 'Runtime smoke rule mode app',
      appType: 'WEB_APP',
    }),
  })
  const task = await api(userToken, '/generation-tasks', {
    method: 'POST',
    body: JSON.stringify({
      workspaceId: workspace.id,
      appId: app.id,
      taskType: 'RULE_GENERATION',
      requirement: 'Create a simple responsive landing page with navigation, main content, and footer.',
      idempotencyKey: `runtime-smoke-${suffix}`,
    }),
  })

  const deadline = Date.now() + 120_000
  let detail
  while (Date.now() < deadline) {
    detail = await api(userToken, `/generation-tasks/${task.taskId}`)
    if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(detail.taskStatus)) {
      break
    }
    await new Promise((resolve) => setTimeout(resolve, 2000))
  }
  requireEqual(detail?.taskStatus, 'SUCCESS', 'generation task status')

  const events = await api(userToken, `/generation-tasks/${task.taskId}/events`)
  const versionEvent = [...events].reverse().find((event) => eventType(event) === 'VERSION_CREATED')
  const versionId = String(eventPayload(versionEvent).versionId || eventPayload(versionEvent).appVersionId || '')
  assert.ok(versionId)

  const appVersionCount = mysqlScalar(`SELECT COUNT(*) FROM app_version WHERE id = ${versionId} AND app_id = ${app.id} AND is_deleted = 0`)
  requireEqual(appVersionCount, '1', 'app_version row count')
  const generatedFileCount = Number(mysqlScalar(`SELECT COUNT(*) FROM generated_file WHERE app_version_id = ${versionId} AND is_deleted = 0`))
  assert.ok(generatedFileCount > 0)

  const files = await api(userToken, `/apps/${app.id}/versions/${versionId}/files`)
  assert.ok(Array.isArray(files))
  assert.ok(files.length > 0)
  const previewResult = await apiRaw(userToken, `/apps/${app.id}/versions/${versionId}/preview-token`, { method: 'POST' })
  const preview = previewResult.data
  assert.ok(preview.previewUrl)
  const setCookie = previewResult.response.headers.get('set-cookie') || ''
  const previewCookie = setCookie.split(';')[0]
  assert.match(previewCookie, /^codeforge_preview_token=/)
  const previewUrl = preview.previewUrl.startsWith('http')
    ? preview.previewUrl
    : `${baseUrl.replace(/\/api\/v1$/, '')}${preview.previewUrl}`
  const previewResponse = await fetch(previewUrl, { headers: { Cookie: previewCookie } })
  requireEqual(previewResponse.status, 200, 'preview HTTP status')
  assert.match(previewResponse.headers.get('content-type') || '', /text\/html/)

  console.log(JSON.stringify({ ok: true, taskStatus: detail.taskStatus, fileCount: generatedFileCount }))
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
