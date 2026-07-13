#!/usr/bin/env node
/**
 * Prompt runtime binding release gate.
 * Does NOT create AI tasks. Verifies an existing task fingerprint + artifact access.
 */

import {
  evaluateReleaseGate,
  GATE_MODES,
  EXIT_CODES,
  resolveExitCode,
} from './lib/prompt-runtime-gate-evaluator.mjs'
import { runBrowserGate } from './lib/prompt-runtime-browser-runner.mjs'

function parseArgs(argv) {
  const options = { taskId: null, modelCallId: null, apiOnly: false }
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--task-id') options.taskId = argv[++index]
    else if (arg === '--model-call-id') options.modelCallId = argv[++index]
    else if (arg === '--api-only') options.apiOnly = true
  }
  if (!options.taskId) {
    const error = new Error('--task-id is required')
    error.exitCode = EXIT_CODES.CONFIG
    throw error
  }
  return options
}

function baseUrl() {
  return (process.env.CODEFORGE_API_BASE_URL || 'http://127.0.0.1:8150/api/v1').replace(/\/$/, '')
}

async function login(account, password) {
  const response = await fetch(`${baseUrl()}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password }),
  })
  const body = await response.json()
  if (!response.ok || body.code !== 0) {
    throw new Error(`login failed for ${account}: ${body.message || response.status}`)
  }
  return body.data.accessToken
}

async function api(token, path, options = {}) {
  const response = await fetch(`${baseUrl()}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  })
  const body = await response.json().catch(() => null)
  return { status: response.status, body }
}

function requireEnv(name) {
  const value = process.env[name]
  if (!value) {
    const error = new Error(`missing required env ${name}`)
    error.exitCode = EXIT_CODES.CONFIG
    throw error
  }
  return value
}

async function verifyArtifacts(userToken, appId, appVersionId) {
  const fileList = await api(userToken, `/apps/${appId}/versions/${appVersionId}/files`)
  const index = await api(
    userToken,
    `/apps/${appId}/versions/${appVersionId}/files/content?filePath=${encodeURIComponent('index.html')}`,
  )
  return {
    fileListHttp: fileList.status,
    indexHttp: index.status,
    indexLength:
      index.body?.code === 0
        ? String(index.body?.data?.content || index.body?.data?.fileContent || '').length
        : 0,
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const mode = args.apiOnly ? GATE_MODES.API_ONLY : GATE_MODES.RELEASE

  const adminToken = await login(requireEnv('CODEFORGE_ADMIN_ACCOUNT'), requireEnv('CODEFORGE_ADMIN_PASSWORD'))
  const userToken = await login(requireEnv('CODEFORGE_USER_ACCOUNT'), requireEnv('CODEFORGE_USER_PASSWORD'))

  const before = await api(adminToken, '/admin/model-call-logs?pageNo=1&pageSize=1')
  const beforeCount = before.body?.data?.total ?? null

  const query = new URLSearchParams({ taskId: String(args.taskId) })
  if (args.modelCallId) query.set('modelCallId', String(args.modelCallId))
  const gate = await api(adminToken, `/admin/release-gates/prompt-runtime-binding?${query.toString()}`)
  if (gate.status !== 200 || gate.body?.code !== 0) {
    throw new Error(`fingerprint gate failed: HTTP ${gate.status} ${gate.body?.message || ''}`)
  }
  const data = gate.body.data

  const artifactApi = data.artifactVersionResolved && data.appId && data.appVersionId
    ? await verifyArtifacts(userToken, data.appId, data.appVersionId)
    : { fileListHttp: 0, indexHttp: 0, indexLength: 0 }

  let foreignArtifact = null
  if (process.env.CODEFORGE_FOREIGN_ACCOUNT && process.env.CODEFORGE_FOREIGN_PASSWORD && data.appId && data.appVersionId) {
    const foreignToken = await login(process.env.CODEFORGE_FOREIGN_ACCOUNT, process.env.CODEFORGE_FOREIGN_PASSWORD)
    const foreignFiles = await api(foreignToken, `/apps/${data.appId}/versions/${data.appVersionId}/files`)
    const foreignIndex = await api(
      foreignToken,
      `/apps/${data.appId}/versions/${data.appVersionId}/files/content?filePath=${encodeURIComponent('index.html')}`,
    )
    foreignArtifact = { fileListHttp: foreignFiles.status, indexHttp: foreignIndex.status }
  }

  let browser = { skipped: true, consoleErrors: 0, pageErrors: 0, http5xx: 0, http5xxEvents: [] }
  if (mode === GATE_MODES.RELEASE && data.artifactVersionResolved && data.appId && data.appVersionId) {
    browser = await runBrowserGate({
      appId: data.appId,
      appVersionId: data.appVersionId,
      account: requireEnv('CODEFORGE_USER_ACCOUNT'),
      password: requireEnv('CODEFORGE_USER_PASSWORD'),
      origin: process.env.CODEFORGE_FRONTEND_ORIGIN || 'http://127.0.0.1:5182',
    })
  }

  const after = await api(adminToken, '/admin/model-call-logs?pageNo=1&pageSize=1')
  const afterCount = after.body?.data?.total ?? null
  const delta = beforeCount == null || afterCount == null ? null : afterCount - beforeCount

  const result = evaluateReleaseGate({
    mode,
    fingerprint: {
      matchesPinnedVersion: data.matchesPinnedVersion,
      matchesLatestVersion: data.matchesLatestVersion,
      v1SystemMatch: data.v1SystemMatch,
      v1UserMatch: data.v1UserMatch,
      v1CombinedMatch: data.v1CombinedMatch,
      v2SystemMatch: data.v2SystemMatch,
      v2UserMatch: data.v2UserMatch,
      v2CombinedMatch: data.v2CombinedMatch,
    },
    artifact: {
      appId: data.appId,
      appVersionId: data.appVersionId,
      bindingSource: data.artifactBindingSource,
      versionResolved: data.artifactVersionResolved,
      bindingErrorCode: data.artifactBindingErrorCode,
      ...artifactApi,
    },
    browser,
    foreignArtifact,
    aiCallBudget: {
      before: beforeCount,
      after: afterCount,
      delta: delta ?? -1,
    },
  })

  const releaseResult = {
    taskId: data.taskId,
    modelCallId: data.modelCallId,
    generationSource: data.generationSource,
    attemptPhase: data.attemptPhase,
    ...result,
  }

  console.log(JSON.stringify(releaseResult, null, 2))
  const exitCode = resolveExitCode(result, browser.unavailable === true)
  process.exit(exitCode)
}

main().catch((error) => {
  console.error(JSON.stringify({ error: String(error) }, null, 2))
  process.exit(error.exitCode ?? EXIT_CODES.FAIL)
})
