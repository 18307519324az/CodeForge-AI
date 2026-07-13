import { createRequire } from 'node:module'
import { mkdir, stat } from 'node:fs/promises'
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

const requiredEnv = [
  'CODEFORGE_SCREENSHOT_BASE_URL',
  'CODEFORGE_SCREENSHOT_ADMIN_USERNAME',
  'CODEFORGE_SCREENSHOT_ADMIN_PASSWORD',
  'CODEFORGE_SCREENSHOT_USER_USERNAME',
  'CODEFORGE_SCREENSHOT_USER_PASSWORD',
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

async function api(token, pathText, options = {}) {
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

function records(pageData) {
  return pageData?.records || pageData?.list || []
}

async function resolveScreenshotData(userToken) {
  const appPage = await api(userToken, '/apps?pageNo=1&pageSize=20')
  const app = records(appPage).find((item) => item?.id)
  if (!app) {
    throw new Error('SCREENSHOT_APP_NOT_FOUND')
  }
  const appId = String(app.id)
  const versionPage = await api(userToken, `/apps/${encodeURIComponent(appId)}/versions?pageNo=1&pageSize=20`)
  const version = records(versionPage).find((item) => item?.id)
  if (!version) {
    throw new Error('SCREENSHOT_VERSION_NOT_FOUND')
  }
  const versionId = String(version.id)
  return { appId, versionId }
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
  await page.screenshot({
    path: tempPath,
    type: 'png',
    fullPage: false,
  })
  convertPngToWebp(tempPath, outputPath, 86)
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

async function createOverview(browser) {
  const script = [
    'from PIL import Image, ImageDraw, ImageFont',
    'import sys, os',
    'base = sys.argv[1]',
    'out = sys.argv[2]',
    'items = [("Workbench", "01-home-workbench.webp"), ("Generation", "02-generation-workbench.webp"), ("Artifacts", "03-artifact-preview.webp"), ("Admin", "05-admin-overview.webp")]',
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
      <div><h1>CodeForge AI</h1><p>安全、可审计的 AI 应用生成与发布平台</p><div class="pill">v1.0.0 Initial Public Release</div></div>
    </div></body></html>`
  await page.setContent(html, { waitUntil: 'load' })
  await page.screenshot({
    path: path.join(imageDir, 'social-preview.png'),
    type: 'png',
    fullPage: false,
  })
  await page.close()
}

async function main() {
  await mkdir(imageDir, { recursive: true })
  const userToken = await login(
    readRequiredEnv('CODEFORGE_SCREENSHOT_USER_USERNAME'),
    readRequiredEnv('CODEFORGE_SCREENSHOT_USER_PASSWORD'),
  )
  const adminToken = await login(
    readRequiredEnv('CODEFORGE_SCREENSHOT_ADMIN_USERNAME'),
    readRequiredEnv('CODEFORGE_SCREENSHOT_ADMIN_PASSWORD'),
  )
  const { appId, versionId } = await resolveScreenshotData(userToken)

  const browser = await chromium.launch({ headless: true })
  const userPage = await browser.newPage({ viewport: { width: 1440, height: 960 }, deviceScaleFactor: 1 })
  await setToken(userPage, userToken)
  await capture(userPage, '/', '01-home-workbench.webp')
  await capture(userPage, `/apps/${encodeURIComponent(appId)}/generate`, '02-generation-workbench.webp')
  await capture(userPage, `/apps/${encodeURIComponent(appId)}/files?versionId=${encodeURIComponent(versionId)}`, '03-artifact-preview.webp')
  await capture(userPage, '/explore', '04-marketplace.webp')
  await userPage.close()

  const adminPage = await browser.newPage({ viewport: { width: 1440, height: 960 }, deviceScaleFactor: 1 })
  await setToken(adminPage, adminToken)
  await capture(adminPage, '/admin/dashboard', '05-admin-overview.webp')
  await capture(adminPage, '/admin/model-providers', '06-provider-routing.webp')
  await capture(adminPage, '/admin/prompt-templates', '07-prompt-versioning.webp')
  await capture(adminPage, '/admin/model-call-logs', '08-model-call-audit.webp')
  await adminPage.close()

  await createOverview(browser)
  await createSocialPreview(browser)
  await browser.close()

  for (const fileName of [
    '01-home-workbench.webp',
    '02-generation-workbench.webp',
    '03-artifact-preview.webp',
    '04-marketplace.webp',
    '05-admin-overview.webp',
    '06-provider-routing.webp',
    '07-prompt-versioning.webp',
    '08-model-call-audit.webp',
    'codeforge-overview.webp',
    'social-preview.png',
  ]) {
    await ensureNonEmpty(fileName)
  }
  console.log(JSON.stringify({ ok: true, appId, versionId, outputDir: 'docs/images' }))
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
