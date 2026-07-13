import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '../..')

function read(relativePath) {
  return readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

function composeCommand() {
  try {
    execFileSync('docker', ['compose', 'version'], { stdio: 'ignore' })
    return { exe: 'docker', prefix: ['compose'] }
  } catch {
    execFileSync('docker-compose', ['version'], { stdio: 'ignore' })
    return { exe: 'docker-compose', prefix: [] }
  }
}

function composeConfig(envFile) {
  const command = composeCommand()
  const args = [...command.prefix]
  if (envFile) {
    args.push('--env-file', envFile)
  }
  args.push('config')
  return execFileSync(command.exe, args, {
    cwd: projectRoot,
    encoding: 'utf8',
    env: {
      PATH: process.env.PATH,
      COMPOSE_PROJECT_NAME: `codeforge-test-${Date.now()}`,
    },
  })
}

function writeTempEnv(lines) {
  const dir = mkdtempSync(path.join(tmpdir(), 'codeforge-compose-'))
  const file = path.join(dir, '.env.local')
  writeFileSync(file, lines.join('\n'), 'utf8')
  return { dir, file }
}

test('ComposeRequiresMysqlRootPasswordTest', () => {
  const temp = writeTempEnv([
    'MYSQL_DATABASE=codeforge_ai',
    'MYSQL_USER=codeforge_ai_user',
    'MYSQL_PASSWORD=strong_application',
  ])
  try {
    assert.throws(() => composeConfig(temp.file), /MYSQL_ROOT_PASSWORD is required/)
  } finally {
    rmSync(temp.dir, { recursive: true, force: true })
  }
})

test('ComposeRequiresMysqlApplicationPasswordTest', () => {
  const temp = writeTempEnv([
    'MYSQL_DATABASE=codeforge_ai',
    'MYSQL_ROOT_PASSWORD=strong_root',
    'MYSQL_USER=codeforge_ai_user',
  ])
  try {
    assert.throws(() => composeConfig(temp.file), /MYSQL_PASSWORD is required/)
  } finally {
    rmSync(temp.dir, { recursive: true, force: true })
  }
})

test('ComposeLoadsEnvLocalTest', () => {
  const temp = writeTempEnv([
    'MYSQL_DATABASE=codeforge_ai',
    'MYSQL_ROOT_PASSWORD=strong_root',
    'MYSQL_USER=codeforge_ai_user',
    'MYSQL_PASSWORD=strong_application',
  ])
  try {
    const config = composeConfig(temp.file)
    assert.match(config, /MYSQL_DATABASE:\s+codeforge_ai/)
    assert.match(config, /MYSQL_PASSWORD:\s+strong_application/)
  } finally {
    rmSync(temp.dir, { recursive: true, force: true })
  }
})

test('ComposeContainsNoWeakPasswordFallbackTest', () => {
  const compose = read('docker-compose.yml')
  assert.equal(/please_change_me|admin123|root123|\$\{MYSQL_PASSWORD:-|\$\{MYSQL_ROOT_PASSWORD:-/.test(compose), false)
})

test('ComposeContainsNoDeprecatedVersionFieldTest', () => {
  assert.equal(/^version:/m.test(read('docker-compose.yml')), false)
})

test('ComposeContainsNoFixedContainerNameTest', () => {
  assert.equal(/\bcontainer_name\s*:/.test(read('docker-compose.yml')), false)
})

test('DocumentationUsesEnvFileOptionTest', () => {
  for (const file of ['README.md', 'README.en.md', 'docs/deployment.md', 'docker/README.md', 'docs/troubleshooting.md']) {
    const text = read(file)
    assert.match(text, /docker compose --env-file \.env\.local up -d mysql redis/, file)
    assert.equal(/docker compose up -d mysql redis/.test(text), false, file)
  }
})

test('MysqlAndApplicationPasswordsRemainConsistentTest', () => {
  const envExample = read('.env.example')
  assert.match(envExample, /^MYSQL_PASSWORD=change_me_codeforge_db_password$/m)
  assert.match(envExample, /^DB_PASSWORD=change_me_codeforge_db_password$/m)
})
