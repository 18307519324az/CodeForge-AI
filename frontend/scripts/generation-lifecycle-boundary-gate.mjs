import { chromium } from '@playwright/test'
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const API = 'http://127.0.0.1:8150/api/v1'
const FRONTEND = 'http://127.0.0.1:5182'
const RESULT_PATH = join(__dirname, '../test-results/generation-lifecycle-boundary-gate.json')
const REQUIREMENT =
  '生成一个简洁的中文待办事项页面，包含输入框、添加按钮、待办列表和完成状态切换，使用原生 HTML、CSS、JavaScript，页面风格简洁现代。'

async function api(method, path, token, body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error(`${method} ${path} failed: ${json.message || res.status}`)
  return json.data
}

async function login() {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account: 'admin', password: 'admin123' }),
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error(`login failed: ${json.message}`)
  return json.data.accessToken
}

const report = {
  startedAt: new Date().toISOString(),
  task: { appId: null, taskId: null },
  baseline: null,
  afterFailure: {},
  gate: {},
}

async function main() {
  mkdirSync(dirname(RESULT_PATH), { recursive: true })
  const token = await login()
  const browser = await chromium.launch({ headless: true, channel: 'chrome' }).catch(() =>
    chromium.launch({ headless: true }),
  )
  const context = await browser.newContext()
  await context.addInitScript((accessToken) => {
    localStorage.setItem('codeforge_access_token', accessToken)
  }, token)

  const page = await context.newPage()
  const network = { stream: [], polling: [] }
  page.on('request', (req) => {
    if (req.method() !== 'GET') return
    const url = req.url()
    if (/\/generation-tasks\/\d+\/stream/.test(url)) network.stream.push(Date.now())
    if (/\/generation-tasks\/\d+/.test(url) && !url.includes('/stream')) {
      if (url.includes('/events') || /\/generation-tasks\/\d+(\?|$)/.test(url)) {
        network.polling.push(Date.now())
      }
    }
  })

  const workspace = await api('POST', '/workspaces', token, {
    name: `LifecycleGate ${Date.now()}`,
    description: 'lifecycle boundary gate',
  })
  const app = await api('POST', '/apps', token, {
    workspaceId: workspace.id,
    name: `LifecycleGate ${Date.now()}`,
    description: REQUIREMENT,
    appType: 'WEB_APP',
  })
  report.task.appId = String(app.id)

  await page.goto(`${FRONTEND}/apps/${app.id}/generate`, { waitUntil: 'load' })
  await page.locator('textarea').fill(REQUIREMENT)
  await page.getByRole('button', { name: '开始生成' }).click()

  // Create POST may block until generation finishes. Poll app.latestTaskId so observation
  // can start while the task is still RUNNING, instead of waiting only for route replace.
  let taskId = null
  const taskPollDeadline = Date.now() + 180_000
  while (Date.now() < taskPollDeadline) {
    const appData = await api('GET', `/apps/${app.id}`, token)
    if (appData.latestTaskId) {
      taskId = String(appData.latestTaskId)
      break
    }
    await page.waitForTimeout(500)
  }
  if (!taskId) {
    throw new Error('taskId missing from app.latestTaskId poll')
  }
  report.task.taskId = taskId

  if (!page.url().includes(`taskId=${taskId}`)) {
    await page.goto(`${FRONTEND}/apps/${app.id}/generate?taskId=${taskId}`, { waitUntil: 'load' })
  } else {
    await page.waitForURL(new RegExp(`taskId=${taskId}`), { timeout: 180_000 })
  }

  const deadline = Date.now() + 420_000
  while (Date.now() < deadline) {
    const label = await page.locator('.connection-label').first().innerText().catch(() => '')
    const events = await api('GET', `/generation-tasks/${taskId}/events`, token)
    const modelDeltas = events.filter((e) => e.type === 'MODEL_DELTA').length
    const modelStarted = events.some((e) => e.type === 'MODEL_CALL_STARTED')
    if (label === '实时' && modelStarted && modelDeltas >= 2) {
      report.baseline = { label, modelDeltas }
      break
    }
    await page.waitForTimeout(800)
  }
  if (!report.baseline) throw new Error('baseline not reached')

  let streamAborted = false
  await page.route(`**/generation-tasks/${taskId}/stream**`, (route) => {
    if (route.request().method() !== 'GET') {
      route.continue()
      return
    }
    streamAborted = true
    route.abort('failed')
  })

  const eventsBeforeFailure = (await api('GET', `/generation-tasks/${taskId}/events`, token)).length
  const failureAt = Date.now()
  await page.reload({ waitUntil: 'load' })
  await page.waitForTimeout(30_000)

  const eventsAfterFailure = (await api('GET', `/generation-tasks/${taskId}/events`, token)).length
  const connectionLabel = await page.locator('.connection-label').first().innerText().catch(() => '')

  const task = await api('GET', `/generation-tasks/${taskId}`, token)
  const pageTaskId = await page.locator('.task-id').first().innerText().catch(() => '')
  const urlTaskId = page.url().match(/taskId=(\d+)/)?.[1]

  const streamAfter = network.stream.filter((at) => at >= failureAt)
  const pollAfter = network.polling.filter((at) => at >= failureAt)

  report.afterFailure = {
    taskStatus: task.taskStatus,
    pageTaskId,
    urlTaskId,
    streamGetsAfterFailure: streamAfter.length,
    pollingRequestsAfterFailure: pollAfter.length,
    streamAborted,
    connectionLabelAfterFailure: connectionLabel,
    eventsBeforeFailure,
    eventsAfterFailure,
    recoveryActivity:
      streamAfter.length + pollAfter.length > 0 ||
      eventsAfterFailure > eventsBeforeFailure ||
      connectionLabel.includes('同步状态') ||
      connectionLabel.includes('恢复实时连接'),
  }

  report.gate = {
    taskRunning: task.taskStatus === 'RUNNING',
    taskIdPreserved: urlTaskId === taskId && pageTaskId.includes(`#${taskId}`),
    recoveryNotKilled:
      pollAfter.length >= 1 ||
      streamAfter.length >= 1 ||
      eventsAfterFailure > eventsBeforeFailure ||
      connectionLabel.includes('同步状态') ||
      connectionLabel.includes('恢复实时连接') ||
      connectionLabel === '实时' ||
      connectionLabel.includes('正在连接'),
    allPass: false,
  }
  report.gate.allPass = Object.entries(report.gate)
    .filter(([k]) => k !== 'allPass')
    .every(([, v]) => v === true)

  report.completedAt = new Date().toISOString()
  writeFileSync(RESULT_PATH, JSON.stringify(report, null, 2), 'utf8')
  console.log(JSON.stringify(report, null, 2))
  await browser.close()
  if (!report.gate.allPass) process.exit(1)
}

main().catch((error) => {
  report.error = String(error?.stack || error)
  writeFileSync(RESULT_PATH, JSON.stringify(report, null, 2), 'utf8')
  console.error(error)
  process.exit(1)
})
