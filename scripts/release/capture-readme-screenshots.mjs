import { createRequire } from 'node:module'
import { mkdir, stat, writeFile } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '../..')
const frontendRoot = path.join(projectRoot, 'frontend')
const require = createRequire(import.meta.url)
const { chromium } = require(require.resolve('playwright', { paths: [frontendRoot] }))

const imageDir = path.join(projectRoot, 'docs/images')
const evidenceDir = path.join(projectRoot, '.local-data/release-evidence')

const requiredEnv = [
  'CODEFORGE_SCREENSHOT_BASE_URL',
  'CODEFORGE_SCREENSHOT_ADMIN_USERNAME',
  'CODEFORGE_SCREENSHOT_ADMIN_PASSWORD',
  'CODEFORGE_SCREENSHOT_USER_USERNAME',
  'CODEFORGE_SCREENSHOT_USER_PASSWORD',
  'CODEFORGE_SCREENSHOT_USER_B_USERNAME',
  'CODEFORGE_SCREENSHOT_USER_B_PASSWORD',
]

function readRequiredEnv(name) {
  const value = process.env[name]
  if (!value || !value.trim()) {
    throw new Error(`missing required env ${name}`)
  }
  return value.trim()
}

for (const name of requiredEnv) {
  readRequiredEnv(name)
}

const baseUrl = readRequiredEnv('CODEFORGE_SCREENSHOT_BASE_URL').replace(/\/$/, '')
const apiBaseUrl = `${baseUrl}/api/v1`
const demoDbName = (process.env.DB_NAME || '').trim()

async function apiRaw(token, pathText, options = {}) {
  const response = await fetch(`${apiBaseUrl}${pathText}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  })
  const json = await response.json().catch(() => ({}))
  return { response, json }
}

async function api(token, pathText, options = {}) {
  const { response, json } = await apiRaw(token, pathText, options)
  if (!response.ok || json.code !== 0) {
    throw new Error(`api request failed ${pathText}`)
  }
  return json.data
}

async function login(account, password) {
  const data = await api('', '/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password }),
  })
  if (!data?.accessToken) {
    throw new Error('login response missing accessToken')
  }
  return data.accessToken
}

async function registerOrLogin(account, password, displayName) {
  const emailSuffix = `${Date.now()}-${Math.random().toString(16).slice(2)}`
  const registration = await apiRaw('', '/auth/register', {
    method: 'POST',
    body: JSON.stringify({
      account,
      password,
      confirmPassword: password,
      displayName,
      email: `${account}-${emailSuffix}@example.invalid`,
    }),
  })
  if (!registration.response.ok && registration.response.status >= 500) {
    throw new Error(`register failed ${account}`)
  }
  return login(account, password)
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

async function findModelCallForTask(adminToken, taskId) {
  const logs = await api(adminToken, '/admin/model-call-logs?pageNo=1&pageSize=20')
  const records = Array.isArray(logs?.records) ? logs.records : []
  return records.find((record) => String(record.taskId) === String(taskId)) || null
}

async function assertDeepSeekAiDirect(adminToken, taskId) {
  const modelCall = await findModelCallForTask(adminToken, taskId)
  if (!modelCall) {
    throw new Error('generated preview model call evidence missing')
  }
  if (!String(modelCall.generationSource || '').startsWith('AI_DIRECT')) {
    throw new Error(`generated preview did not use AI_DIRECT: ${modelCall.generationSource || 'UNKNOWN'}`)
  }
  if (modelCall.fallbackUsed) {
    throw new Error('generated preview used fallback output')
  }
  if (modelCall.providerCode !== 'deepseek') {
    throw new Error(`generated preview did not use deepseek: ${modelCall.providerCode || 'UNKNOWN'}`)
  }
  return modelCall
}

async function buildGeneratedEvidence(userToken, adminToken, appId, taskId, versionId, versionNo) {
  const files = await api(userToken, `/apps/${encodeURIComponent(appId)}/versions/${encodeURIComponent(versionId)}/files`)
  if (!Array.isArray(files) || files.length === 0) {
    throw new Error('generated version has no files')
  }
  const preview = await api(userToken, `/apps/${encodeURIComponent(appId)}/versions/${encodeURIComponent(versionId)}/preview-token`, {
    method: 'POST',
  })
  if (!preview?.previewUrl) {
    throw new Error('preview token response missing previewUrl')
  }
  const previewTokenMatch = preview.previewUrl.match(/[?&]previewToken=([^&]+)/)
  if (!previewTokenMatch) {
    throw new Error('preview token response missing previewToken')
  }
  const modelCall = await assertDeepSeekAiDirect(adminToken, taskId)
  return {
    appId: String(appId),
    taskId: String(taskId),
    versionId: String(versionId),
    versionNo: versionNo || null,
    fileCount: files.length,
    generationSource: modelCall.generationSource,
    fallbackUsed: modelCall.fallbackUsed,
    providerCode: modelCall.providerCode,
    modelName: modelCall.modelName || null,
    previewUrl: preview.previewUrl,
    previewToken: decodeURIComponent(previewTokenMatch[1]),
  }
}

async function createGeneratedWebsite(userToken, adminToken) {
  const suffix = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
  const workspace = await api(userToken, '/workspaces/default', { method: 'POST' })
  const app = await api(userToken, '/apps', {
    method: 'POST',
    body: JSON.stringify({
      workspaceId: workspace.id,
      name: `Aurora SaaS Landing Page ${suffix}`,
      description: 'Release documentation generated website preview',
      appType: 'WEB_APP',
    }),
  })
  const requirement = [
    'Generate a modern SaaS product website named Aurora SaaS Landing Page.',
    'Include top navigation, gradient hero, benefit cards, process steps, pricing plans, testimonials, and footer.',
    'Use responsive HTML, CSS, and a small amount of JavaScript.',
  ].join(' ')
  const task = await api(userToken, '/generation-tasks', {
    method: 'POST',
    body: JSON.stringify({
      workspaceId: workspace.id,
      appId: app.id,
      taskType: 'RULE_GENERATION',
      requirement,
      idempotencyKey: `readme-generated-preview-${suffix}`,
    }),
  })
  const taskId = task.taskId
  const deadline = Date.now() + 240_000
  let detail
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 3000))
    detail = await api(userToken, `/generation-tasks/${encodeURIComponent(taskId)}`)
    if (['SUCCESS', 'FAILED', 'CANCELLED'].includes(detail.taskStatus)) {
      break
    }
  }
  if (!detail || detail.taskStatus !== 'SUCCESS') {
    throw new Error(`generation task did not succeed: ${detail?.taskStatus || 'UNKNOWN'}`)
  }
  const events = await api(userToken, `/generation-tasks/${encodeURIComponent(taskId)}/events`)
  const successEvent = [...events].reverse().find((event) => eventType(event) === 'TASK_SUCCESS')
  const versionEvent = [...events].reverse().find((event) => eventType(event) === 'VERSION_CREATED')
  if (!successEvent || !versionEvent) {
    throw new Error('generation terminal or version event missing')
  }
  const payload = eventPayload(versionEvent)
  const versionId = String(payload.versionId || payload.appVersionId || '')
  if (!versionId) {
    throw new Error('generation success event missing versionId')
  }
  return buildGeneratedEvidence(userToken, adminToken, app.id, taskId, versionId, payload.versionNo)
}

async function resolveGeneratedWebsite(userToken, adminToken) {
  const existingAppId = (process.env.CODEFORGE_SCREENSHOT_EXISTING_APP_ID || '').trim()
  const existingTaskId = (process.env.CODEFORGE_SCREENSHOT_EXISTING_TASK_ID || '').trim()
  const existingVersionId = (process.env.CODEFORGE_SCREENSHOT_EXISTING_VERSION_ID || '').trim()
  if (existingAppId || existingTaskId || existingVersionId) {
    if (!existingAppId || !existingTaskId || !existingVersionId) {
      throw new Error('existing generated preview requires appId, taskId, and versionId')
    }
    return buildGeneratedEvidence(userToken, adminToken, existingAppId, existingTaskId, existingVersionId, null)
  }
  return createGeneratedWebsite(userToken, adminToken)
}

async function setToken(page, token) {
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' })
  await page.evaluate((accessToken) => {
    localStorage.setItem('codeforge_access_token', accessToken)
  }, token)
}

async function stabilize(page) {
  await page.addStyleTag({
    content: `
      * { caret-color: transparent !important; }
      .ant-message, .ant-notification { display: none !important; }
      [data-screenshot-hide="true"] { visibility: hidden !important; }
    `,
  })
}

async function capture(page, route, fileName) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: 'networkidle' })
  await stabilize(page)
  const outputPath = path.join(imageDir, fileName)
  const tempPath = outputPath.replace(/\.webp$/i, '.tmp.png')
  await page.screenshot({ path: tempPath, type: 'png', fullPage: false })
  convertPngToWebp(tempPath, outputPath, 86)
}

async function captureAbsolute(page, url, fileName) {
  await page.goto(url, { waitUntil: 'networkidle' })
  await stabilize(page)
  const visibleText = await page.locator('body').innerText().catch(() => '')
  if (visibleText.trim().length < 40) {
    throw new Error('generated preview page has insufficient visible text')
  }
  const outputPath = path.join(imageDir, fileName)
  const tempPath = outputPath.replace(/\.webp$/i, '.tmp.png')
  await page.screenshot({ path: tempPath, type: 'png', fullPage: false })
  convertPngToWebp(tempPath, outputPath, 86)
}

async function setPreviewCookie(context, previewToken) {
  await context.addCookies([
    {
      name: 'codeforge_preview_token',
      value: previewToken,
      domain: '127.0.0.1',
      path: '/api/v1/static-preview',
      httpOnly: true,
      sameSite: 'Lax',
      secure: false,
    },
  ])
}

function convertPngToWebp(sourcePath, targetPath, quality) {
  const script = [
    'from PIL import Image',
    'import sys, os',
    'src, dst, quality = sys.argv[1], sys.argv[2], int(sys.argv[3])',
    'with Image.open(src) as img:',
    '    img.save(dst, "WEBP", quality=quality, method=6)',
    'os.remove(src)',
  ].join('\n')
  execFileSync('python', ['-c', script, sourcePath, targetPath, String(quality)], { stdio: 'ignore' })
}

async function ensureNonEmpty(fileName) {
  const info = await stat(path.join(imageDir, fileName))
  if (info.size <= 1024) {
    throw new Error(`screenshot too small ${fileName}`)
  }
}

async function createOverview() {
  const script = [
    'from PIL import Image, ImageDraw, ImageFont',
    'import sys, os',
    'base = sys.argv[1]',
    'out = sys.argv[2]',
    'items = [("Workbench", "01-home-workbench.webp"), ("Generated website", "03-generated-site-preview.webp"), ("Artifacts", "04-artifact-workbench.webp"), ("Admin", "06-admin-overview.webp")]',
    'canvas = Image.new("RGB", (1280, 960), "#f4f6f8")',
    'draw = ImageDraw.Draw(canvas)',
    'font = ImageFont.load_default()',
    'positions = [(24, 24), (650, 24), (24, 492), (650, 492)]',
    'for (title, file_name), (x, y) in zip(items, positions):',
    '    card = Image.new("RGB", (606, 444), "#ffffff")',
    '    cd = ImageDraw.Draw(card)',
    '    cd.rectangle((0, 0, 605, 443), outline="#d9dee7", width=1)',
    '    cd.text((14, 14), title, fill="#111827", font=font)',
    '    with Image.open(os.path.join(base, file_name)) as img:',
    '        img = img.convert("RGB")',
    '        img.thumbnail((604, 388), Image.Resampling.LANCZOS)',
    '        card.paste(img, (1, 55))',
    '    canvas.paste(card, (x, y))',
    'canvas.save(out, "WEBP", quality=88, method=6)',
  ].join('\n')
  execFileSync('python', ['-c', script, imageDir, path.join(imageDir, 'codeforge-overview.webp')], { stdio: 'ignore' })
}

async function createSocialPreview(browser) {
  const page = await browser.newPage({ viewport: { width: 1280, height: 640 }, deviceScaleFactor: 1 })
  const mascotPath = path.join(projectRoot, 'frontend/public/brand/codeforge-mascot.png')
  const mascot = `data:image/png;base64,${readFileSync(mascotPath).toString('base64')}`
  const html = `<!doctype html>
    <html><head><meta charset="utf-8"><style>
      body { margin: 0; width: 1280px; height: 640px; background: #0f172a; color: #fff; font-family: Arial, sans-serif; }
      .wrap { height: 100%; display: flex; align-items: center; gap: 72px; padding: 0 92px; box-sizing: border-box; }
      img { width: 220px; height: 220px; object-fit: contain; background: #fff; border-radius: 32px; padding: 24px; }
      h1 { font-size: 76px; line-height: 1; margin: 0 0 24px; letter-spacing: 0; }
      p { font-size: 30px; line-height: 1.35; margin: 0; color: #cbd5e1; max-width: 760px; }
      .pill { display: inline-block; margin-top: 36px; border: 1px solid #38bdf8; color: #7dd3fc; padding: 12px 18px; border-radius: 8px; font-size: 22px; }
    </style></head><body><div class="wrap">
      <img src="${mascot}" alt="">
      <div><h1>CodeForge AI</h1><p>Secure, auditable AI application generation and release platform.</p><div class="pill">v1.0.1 Professional Release</div></div>
    </div></body></html>`
  await page.setContent(html, { waitUntil: 'load' })
  await page.screenshot({ path: path.join(imageDir, 'social-preview.png'), type: 'png', fullPage: false })
  await page.close()
}

async function main() {
  if (demoDbName && !demoDbName.startsWith('codeforge_ai_docs_demo_')) {
    throw new Error('DB_NAME must be codeforge_ai_docs_demo_* for release screenshots')
  }
  await mkdir(imageDir, { recursive: true })
  await mkdir(evidenceDir, { recursive: true })

  const adminToken = await registerOrLogin(
    readRequiredEnv('CODEFORGE_SCREENSHOT_ADMIN_USERNAME'),
    readRequiredEnv('CODEFORGE_SCREENSHOT_ADMIN_PASSWORD'),
    'Demo Admin',
  )
  const userToken = await registerOrLogin(
    readRequiredEnv('CODEFORGE_SCREENSHOT_USER_USERNAME'),
    readRequiredEnv('CODEFORGE_SCREENSHOT_USER_PASSWORD'),
    'Demo User A',
  )
  await registerOrLogin(
    readRequiredEnv('CODEFORGE_SCREENSHOT_USER_B_USERNAME'),
    readRequiredEnv('CODEFORGE_SCREENSHOT_USER_B_PASSWORD'),
    'Demo User B',
  )
  const generated = await resolveGeneratedWebsite(userToken, adminToken)
  const { appId, versionId } = generated

  const browser = await chromium.launch({ headless: true })
  const userPage = await browser.newPage({ viewport: { width: 1440, height: 960 }, deviceScaleFactor: 1 })
  await setToken(userPage, userToken)
  await capture(userPage, '/', '01-home-workbench.webp')
  await capture(userPage, `/apps/${encodeURIComponent(appId)}/generate`, '02-generation-workbench.webp')
  const previewUrl = generated.previewUrl.startsWith('http')
    ? generated.previewUrl
    : `${baseUrl}${generated.previewUrl.startsWith('/') ? '' : '/'}${generated.previewUrl}`
  await setPreviewCookie(userPage.context(), generated.previewToken)
  await captureAbsolute(userPage, previewUrl, '03-generated-site-preview.webp')
  await capture(userPage, `/apps/${encodeURIComponent(appId)}/files?versionId=${encodeURIComponent(versionId)}`, '04-artifact-workbench.webp')
  await capture(userPage, '/explore', '05-marketplace.webp')
  await userPage.close()

  const adminPage = await browser.newPage({ viewport: { width: 1440, height: 960 }, deviceScaleFactor: 1 })
  await setToken(adminPage, adminToken)
  await capture(adminPage, '/admin/dashboard', '06-admin-overview.webp')
  await capture(adminPage, '/admin/model-providers', '07-provider-routing.webp')
  await capture(adminPage, '/admin/prompt-templates', '08-prompt-versioning.webp')
  await capture(adminPage, '/admin/model-call-logs', '09-model-call-audit.webp')
  await adminPage.close()

  await createOverview()
  await createSocialPreview(browser)
  await browser.close()

  for (const fileName of [
    '01-home-workbench.webp',
    '02-generation-workbench.webp',
    '03-generated-site-preview.webp',
    '04-artifact-workbench.webp',
    '05-marketplace.webp',
    '06-admin-overview.webp',
    '07-provider-routing.webp',
    '08-prompt-versioning.webp',
    '09-model-call-audit.webp',
    'codeforge-overview.webp',
    'social-preview.png',
  ]) {
    await ensureNonEmpty(fileName)
  }

  await writeFile(
    path.join(evidenceDir, 'generated-preview.json'),
    JSON.stringify(
      {
        ok: true,
        appId: generated.appId,
        taskId: generated.taskId,
        versionId: generated.versionId,
        versionNo: generated.versionNo,
        fileCount: generated.fileCount,
        generationSource: generated.generationSource,
        fallbackUsed: generated.fallbackUsed,
        providerCode: generated.providerCode,
        modelName: generated.modelName,
        previewLoaded: true,
        output: 'docs/images/03-generated-site-preview.webp',
      },
      null,
      2,
    ),
  )
  console.log(JSON.stringify({ ok: true, appId, taskId: generated.taskId, versionId, fileCount: generated.fileCount, outputDir: 'docs/images' }))
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
