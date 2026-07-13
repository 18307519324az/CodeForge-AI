import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '../..')

const markdownFiles = [
  'README.md',
  'README.en.md',
  'docs/architecture.md',
  'docs/product-tour.md',
  'docs/configuration.md',
  'docs/security-model.md',
  'docs/deployment.md',
  'docs/troubleshooting.md',
]

const requiredImages = [
  'docs/images/01-home-workbench.webp',
  'docs/images/02-generation-workbench.webp',
  'docs/images/03-generated-site-preview.webp',
  'docs/images/04-artifact-workbench.webp',
  'docs/images/05-marketplace.webp',
  'docs/images/06-admin-overview.webp',
  'docs/images/07-provider-routing.webp',
  'docs/images/08-prompt-versioning.webp',
  'docs/images/09-model-call-audit.webp',
  'docs/images/codeforge-overview.webp',
  'docs/images/social-preview.png',
  'frontend/public/brand/codeforge-mascot.png',
]

const sensitiveTokenPattern = new RegExp(
  `${'github' + '_pat_'}|${'gh' + 'p_'}|${'s' + 'k-'}[A-Za-z0-9_-]{10,}`,
)
const windowsDrivePattern = new RegExp(`${'C'}:[\\\\/]`)
const windowsUserPathPattern = new RegExp(`${'C'}:[\\\\/]Users[\\\\/]`)

function read(relativePath) {
  return readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

function extractImageRefs(markdown) {
  const refs = []
  const mdImage = /!\[[^\]]*]\(([^)]+)\)/g
  const htmlImage = /<img\b[^>]*\bsrc="([^"]+)"/g
  for (const pattern of [mdImage, htmlImage]) {
    for (const match of markdown.matchAll(pattern)) {
      refs.push(match[1].trim())
    }
  }
  return refs
}

function isExternal(ref) {
  return /^https?:\/\//.test(ref)
}

function assertRelativeClean(ref, sourceFile) {
  assert.equal(ref.includes('localhost'), false, `${sourceFile} contains localhost image ref ${ref}`)
  assert.equal(/^[A-Za-z]:[\\/]/.test(ref), false, `${sourceFile} contains absolute image path ${ref}`)
  assert.equal(ref.startsWith('/'), false, `${sourceFile} contains root absolute image path ${ref}`)
  assert.equal(ref.includes('..'), false, `${sourceFile} contains parent traversal image ref ${ref}`)
}

test('README image references exist with exact case', () => {
  for (const file of markdownFiles) {
    const text = read(file)
    for (const ref of extractImageRefs(text)) {
      if (isExternal(ref)) {
        continue
      }
      assertRelativeClean(ref, file)
      const resolved = path.resolve(projectRoot, path.dirname(file), ref)
      assert.equal(existsSync(resolved), true, `${file} references missing image ${ref}`)
      assert.ok(statSync(resolved).size > 1024, `${file} references empty image ${ref}`)
    }
  }
})

test('required release images are present and bounded', () => {
  for (const image of requiredImages) {
    const fullPath = path.join(projectRoot, image)
    assert.equal(existsSync(fullPath), true, `missing required image ${image}`)
    const size = statSync(fullPath).size
    assert.ok(size > 1024, `image too small ${image}`)
    assert.ok(size < 2_500_000, `image too large ${image}`)
  }
})

test('README keeps professional release presentation constraints', () => {
  const readme = read('README.md')
  assert.match(readme, /\[English]\(README\.en\.md\)/)
  assert.match(readme, /docs\/images\/codeforge-overview\.webp/)
  assert.match(readme, /docs\/images\/03-generated-site-preview\.webp/)
  assert.match(readme, /bootstrap-fresh-database\.ps1/)
  assert.match(readme, /```mermaid/)
  assert.equal((readme.match(/img\.shields\.io/g) || []).length <= 8, true)
  assert.equal(/aiAvatar\.png|logo\.png|favicon\.ico/.test(readme), false)
  assert.equal(sensitiveTokenPattern.test(readme), false)
  assert.equal(windowsDrivePattern.test(readme), false)
})

test('documentation does not reference removed legacy assets or real secrets', () => {
  const combined = markdownFiles.map(read).join('\n')
  assert.equal(/aiAvatar\.png|logo\.png|favicon\.ico/.test(combined), false)
  assert.equal(sensitiveTokenPattern.test(combined), false)
  assert.equal(windowsUserPathPattern.test(combined), false)
  assert.equal(/Fresh(?: DB| Database)?[\s\S]{0,160}apply-local-migrations\.ps1/i.test(combined), false)
  assert.equal(/apply-local-migrations\.ps1[\s\S]{0,160}(?:initialize|bootstrap) Fresh/i.test(combined), false)
  assert.match(read('SECURITY.md'), /security\/advisories\/new/)
  assert.equal(existsSync(path.join(projectRoot, '.github/ISSUE_TEMPLATE/security_review.yml')), false)
})

test('Chinese and English README keep required section parity', () => {
  const zh = read('README.md')
  const en = read('README.en.md')
  for (const marker of [
    'Product Overview',
    'Generated Website Preview',
    'Core Capabilities',
    'Role Matrix',
    'Architecture',
    'Generation Flow',
    'Security Model',
    'Quick Start',
    'Fresh Database Bootstrap',
    'Provider Configuration',
    'Tests',
    'Project Layout',
    'Known Limitations',
    'Roadmap',
    'License',
    'Maintainer',
  ]) {
    assert.match(en, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  }
  for (const marker of [
    '产品概览',
    '真实生成网站预览',
    '核心能力',
    '角色矩阵',
    '架构',
    '生成流程',
    '安全模型',
    '快速开始',
    'Fresh Database Bootstrap',
    'Provider 配置',
    '测试',
    '项目结构',
    '已知限制',
    '路线图',
    '许可证',
    '维护者',
  ]) {
    assert.match(zh, new RegExp(marker.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  }
})
