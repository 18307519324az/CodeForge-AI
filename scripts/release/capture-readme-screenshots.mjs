import { createRequire } from 'node:module'
import { mkdir, stat, writeFile } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'

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
  'CODEFORGE_SCREENSHOT_EXPECTED_PROVIDER',
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
const expectedProvider = readRequiredEnv('CODEFORGE_SCREENSHOT_EXPECTED_PROVIDER')

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

async function assertExpectedAiDirect(adminToken, taskId) {
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
  if (modelCall.providerCode !== expectedProvider) {
    throw new Error(`generated preview did not use expected provider: ${modelCall.providerCode || 'UNKNOWN'}`)
  }
  if (!String(modelCall.modelName || '').trim()) {
    throw new Error('generated preview model name missing')
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
  const modelCall = await assertExpectedAiDirect(adminToken, taskId)
  return {
    taskType: 'RULE_GENERATION',
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
  const observations = {
    consoleErrorCount: 0,
    pageErrorCount: 0,
    requestFailureCount: 0,
    unexpected5xxCount: 0,
  }
  const onConsole = (message) => {
    if (message.type() === 'error') {
      observations.consoleErrorCount += 1
    }
  }
  const onPageError = () => {
    observations.pageErrorCount += 1
  }
  const onRequestFailed = () => {
    observations.requestFailureCount += 1
  }
  const onResponse = (response) => {
    if (response.status() >= 500) {
      observations.unexpected5xxCount += 1
    }
  }
  page.on('console', onConsole)
  page.on('pageerror', onPageError)
  page.on('requestfailed', onRequestFailed)
  page.on('response', onResponse)
  const response = await page.goto(url, { waitUntil: 'networkidle' })
  await stabilize(page)
  if (!response) {
    throw new Error('generated preview navigation response missing')
  }
  const status = response.status()
  if (status !== 200) {
    throw new Error(`generated preview HTTP status ${status}`)
  }
  const contentType = response.headers()['content-type'] || ''
  if (!contentType.toLowerCase().includes('text/html')) {
    throw new Error(`generated preview content type ${contentType || 'UNKNOWN'}`)
  }
  const parsedUrl = new URL(page.url())
  if (!parsedUrl.pathname.includes('/api/v1/static-preview')) {
    throw new Error(`generated preview URL is not static-preview: ${parsedUrl.pathname}`)
  }
  const hasSemanticHtml = await page.locator('main, header, nav, section, h1').first().count()
  if (hasSemanticHtml < 1) {
    throw new Error('generated preview semantic HTML missing')
  }
  const visibleText = await page.locator('body').innerText().catch(() => '')
  if (visibleText.trim().length < 80) {
    throw new Error('generated preview page has insufficient visible text')
  }
  const trimmed = visibleText.trim()
  if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    throw new Error('generated preview page is JSON text')
  }
  const forbidden = [
    '登录 CodeForge',
    '管理概览',
    '模型供应商',
    '应用广场',
    'Whitelabel Error Page',
    'Internal Server Error',
    '404 Not Found',
  ]
  for (const marker of forbidden) {
    if (visibleText.includes(marker)) {
      throw new Error(`generated preview contains forbidden marker: ${marker}`)
    }
  }
  const html = await page.content()
  if (/storagePath|[A-Za-z]:[\\/]|\/var\/|\/tmp\/|\/home\//.test(html)) {
    throw new Error('generated preview contains server storage path marker')
  }
  const previewToken = new URL(url).searchParams.get('previewToken')
  if (previewToken && html.includes(previewToken)) {
    throw new Error('generated preview contains preview token')
  }
  if (observations.consoleErrorCount !== 0 || observations.pageErrorCount !== 0 || observations.requestFailureCount !== 0 || observations.unexpected5xxCount !== 0) {
    throw new Error('generated preview browser observations contain errors')
  }
  const outputPath = path.join(imageDir, fileName)
  const tempPath = outputPath.replace(/\.webp$/i, '.tmp.png')
  await page.screenshot({ path: tempPath, type: 'png', fullPage: false })
  convertPngToWebp(tempPath, outputPath, 86)
  const imageEvidence = validateWebpScreenshot(outputPath)
  return {
    previewHttpStatus: status,
    previewContentType: contentType,
    domAssertions: {
      staticPreviewPath: true,
      semanticHtml: true,
      visibleTextLengthAtLeast80: true,
      rejectedLoginAdminJsonAndErrorPages: true,
      serverPathAbsent: true,
      tokenAbsent: true,
    },
    consoleErrorCount: observations.consoleErrorCount,
    pageErrorCount: observations.pageErrorCount,
    requestFailureCount: observations.requestFailureCount,
    screenshotSha256: imageEvidence.sha256,
  }
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

function validateWebpScreenshot(filePath) {
  const script = [
    'from PIL import Image, ImageStat',
    'import hashlib, json, os, sys',
    'path = sys.argv[1]',
    'with Image.open(path) as img:',
    '    if img.format != "WEBP": raise SystemExit("IMAGE_NOT_WEBP")',
    '    width, height = img.size',
    '    if width < 1200 or height < 700: raise SystemExit("IMAGE_DIMENSIONS_TOO_SMALL")',
    '    if os.path.getsize(path) >= 1000000: raise SystemExit("IMAGE_TOO_LARGE")',
    '    rgb = img.convert("RGB")',
    '    colors = rgb.resize((320, 180)).getcolors(maxcolors=320*180)',
    '    if colors is None or len(colors) < 64: raise SystemExit("IMAGE_COLOR_COUNT_TOO_LOW")',
    '    stat = ImageStat.Stat(rgb)',
    '    if max(stat.var) < 20: raise SystemExit("IMAGE_VARIANCE_TOO_LOW")',
    '    mean = sum(stat.mean) / len(stat.mean)',
    '    if mean > 248 or mean < 7: raise SystemExit("IMAGE_MEAN_OUT_OF_RANGE")',
    '    if img.getexif() and len(img.getexif()) > 0: raise SystemExit("IMAGE_HAS_EXIF")',
    'with open(path, "rb") as fh:',
    '    sha256 = hashlib.sha256(fh.read()).hexdigest()',
    'print(json.dumps({"sha256": sha256, "width": width, "height": height}))',
  ].join('\n')
  return JSON.parse(execFileSync('python', ['-c', script, filePath], { encoding: 'utf8' }))
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
  const generatedPreviewEvidence = await captureAbsolute(userPage, previewUrl, '03-generated-site-preview.webp')
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
        timestamp: new Date().toISOString(),
        runtimeHead: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: projectRoot, encoding: 'utf8' }).trim(),
        taskType: generated.taskType,
        fileCount: generated.fileCount,
        generationSource: generated.generationSource,
        fallbackUsed: generated.fallbackUsed,
        providerCode: generated.providerCode,
        modelName: generated.modelName,
        previewHttpStatus: generatedPreviewEvidence.previewHttpStatus,
        previewContentType: generatedPreviewEvidence.previewContentType,
        domAssertions: generatedPreviewEvidence.domAssertions,
        consoleErrorCount: generatedPreviewEvidence.consoleErrorCount,
        pageErrorCount: generatedPreviewEvidence.pageErrorCount,
        requestFailureCount: generatedPreviewEvidence.requestFailureCount,
        screenshotSha256: generatedPreviewEvidence.screenshotSha256,
      },
      null,
      2,
    ),
  )
  console.log(JSON.stringify({ ok: true, fileCount: generated.fileCount, providerCode: generated.providerCode, outputDir: 'docs/images' }))
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
